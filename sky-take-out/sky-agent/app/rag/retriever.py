"""FAQ 知识库检索器：关键词重叠打分，零外部依赖（适合当前小规模知识库）。
后续知识库变大或需语义检索时，可升级为向量库（Chroma/PGVector + embedding）。"""
import hashlib
import os
import re
import tempfile
from threading import RLock
from pathlib import Path

KNOWLEDGE_DIR = Path(__file__).resolve().parent.parent.parent / "knowledge"

_ENTRIES: list[dict] | None = None
FAQ_SOURCE = "knowledge/faq.md"
FAQ_VERSION = "1.0"
_FAQ_LOCK = RLock()


def _entry_id(question: str) -> str:
    return hashlib.sha256(question.encode("utf-8")).hexdigest()[:16]


def _parse_faq() -> list[dict]:
    """解析 knowledge/faq.md：`## 标题` 为问题，其后内容为答案"""
    faq_path = KNOWLEDGE_DIR / "faq.md"
    if not faq_path.exists():
        return []
    text = faq_path.read_text(encoding="utf-8")
    entries = []
    for block in re.split(r"^## ", text, flags=re.M)[1:]:
        lines = block.strip().split("\n", 1)
        question = lines[0].strip()
        answer = lines[1].strip() if len(lines) > 1 else ""
        if question and answer:
            entries.append({"id": _entry_id(question), "question": question, "answer": answer, "source": FAQ_SOURCE,
                            "version": FAQ_VERSION, "effective_at": None, "updated_at": faq_path.stat().st_mtime})
    return entries


def list_faq() -> list[dict]:
    global _ENTRIES
    with _FAQ_LOCK:
        if _ENTRIES is None:
            _ENTRIES = _parse_faq()
        return [dict(entry) for entry in _ENTRIES]


def _validate_entry(question: str, answer: str) -> tuple[str, str]:
    question = (question or "").strip()
    answer = (answer or "").strip()
    if not question or not answer:
        raise ValueError("question and answer are required")
    if len(question) > 200 or len(answer) > 5000:
        raise ValueError("question or answer is too long")
    if "\n" in question or "\r" in question or question.startswith("#"):
        raise ValueError("question must be a single line without markdown headings")
    if any(re.match(r"^##\s", line) for line in answer.splitlines()):
        raise ValueError("answer must not contain level-two markdown headings")
    return question, answer


def _persist(entries: list[dict]) -> None:
    faq_path = KNOWLEDGE_DIR / "faq.md"
    KNOWLEDGE_DIR.mkdir(parents=True, exist_ok=True)
    header = "# 老吴外卖 - 售后政策与常见问题（RAG 知识库）\n# 格式约定：## 问题标题开头为一条知识，其后至下一个 ## 为答案正文。\n\n"
    body = "\n\n".join(f"## {entry['question']}\n{entry['answer']}" for entry in entries)
    fd, temp_path = tempfile.mkstemp(prefix=".faq-", suffix=".md", dir=str(KNOWLEDGE_DIR))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(header + body + "\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_path, faq_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


def create_faq(question: str, answer: str) -> dict:
    global _ENTRIES
    question, answer = _validate_entry(question, answer)
    with _FAQ_LOCK:
        entries = list_faq()
        if any(entry["question"] == question for entry in entries):
            raise ValueError("FAQ question already exists")
        _persist(entries + [{"id": _entry_id(question), "question": question, "answer": answer,
                             "source": FAQ_SOURCE, "version": FAQ_VERSION, "effective_at": None,
                             "updated_at": None}])
        _ENTRIES = _parse_faq()
        return next(item for item in _ENTRIES if item["question"] == question)


def update_faq(entry_id: str, question: str, answer: str) -> dict:
    global _ENTRIES
    question, answer = _validate_entry(question, answer)
    with _FAQ_LOCK:
        entries = list_faq()
        index = next((i for i, item in enumerate(entries) if item["id"] == entry_id), None)
        if index is None:
            raise KeyError(entry_id)
        if any(i != index and item["question"] == question for i, item in enumerate(entries)):
            raise ValueError("FAQ question already exists")
        entries[index] = {"id": _entry_id(question), "question": question, "answer": answer,
                          "source": FAQ_SOURCE, "version": FAQ_VERSION, "effective_at": None,
                          "updated_at": None}
        _persist(entries)
        _ENTRIES = _parse_faq()
        return _ENTRIES[index]


def delete_faq(entry_id: str) -> None:
    global _ENTRIES
    with _FAQ_LOCK:
        entries = list_faq()
        filtered = [item for item in entries if item["id"] != entry_id]
        if len(filtered) == len(entries):
            raise KeyError(entry_id)
        _persist(filtered)
        _ENTRIES = _parse_faq()


def _tokens(s: str) -> set[str]:
    """粗分词：单汉字 + 连续字母数字串（小规模 FAQ 场景够用）"""
    return set(re.findall(r"[\u4e00-\u9fff]|[a-zA-Z0-9]+", s.lower()))


def search(query: str, top_k: int = 2, min_score: int = 2) -> list[dict]:
    """按关键词重叠度检索最相关的 top_k 条 FAQ"""
    global _ENTRIES
    if _ENTRIES is None:
        _ENTRIES = _parse_faq()

    query_tokens = _tokens(query)
    if not query_tokens:
        return []

    scored = []
    for entry in _ENTRIES:
        entry_tokens = _tokens(entry["question"]) | _tokens(entry["answer"])
        overlap = len(query_tokens & entry_tokens)
        # 问题命中权重更高
        q_overlap = len(query_tokens & _tokens(entry["question"]))
        score = overlap + q_overlap
        if score >= min_score:
            scored.append((score, entry))

    scored.sort(key=lambda x: -x[0])
    return [entry for _, entry in scored[:top_k]]
