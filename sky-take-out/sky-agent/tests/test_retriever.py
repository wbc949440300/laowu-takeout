"""RAG 检索器测试：解析 + 相关性"""
from app.rag.retriever import search, _parse_faq


def test_faq_parsed():
    entries = _parse_faq()
    assert len(entries) >= 10, "FAQ 条目应至少 10 条"
    for e in entries:
        assert e["question"] and e["answer"]


def test_search_refund_policy():
    results = search("退款多久到账")
    assert results, "退款相关问题应命中知识库"
    assert any("退款" in r["question"] for r in results)


def test_search_business_hours():
    results = search("你们几点关门")
    assert results, "营业时间问题应命中知识库"
    assert any("营业" in r["question"] for r in results)


def test_search_no_match():
    results = search("今天天气怎么样")
    # 允许弱命中，但不应把天气当作政策条目
    for r in results:
        assert "天气" not in r["answer"]
