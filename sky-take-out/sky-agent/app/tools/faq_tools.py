"""FAQ 知识库工具：政策/咨询类问题先查知识库，回答需注明依据"""
from langchain_core.tools import tool

from app.rag.retriever import search
from app.tools.tool_response import tool_ok


def build_faq_tools():

    @tool
    async def search_faq(query: str) -> str:
        """检索老吴外卖售后政策/常见问题知识库（营业时间、配送范围、送达时长、退款政策、
        取消规则、催单限制、支付方式、优惠券、评价、发票、转人工等）。
        政策/咨询类问题必须先调用本工具，依据返回内容回答，禁止凭记忆回答。"""
        matches = search(query, top_k=2, min_score=2)
        if not matches:
            return tool_ok({"matches": [], "hint": "知识库无匹配内容，请如实告知并引导用户转人工客服"})
        return tool_ok({"matches": matches})

    return [search_faq]
