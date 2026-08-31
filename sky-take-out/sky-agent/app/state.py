"""LangGraph 客服图状态定义"""
from typing import Annotated, TypedDict

from langgraph.graph.message import add_messages


def merge_usage(left: dict | None, right: dict | None) -> dict:
    result = dict(left or {})
    for key, value in (right or {}).items():
        if isinstance(value, (int, float)):
            result[key] = result.get(key, 0) + value
        else:
            result[key] = value
    return result


class AgentState(TypedDict):
    # 对话历史（checkpointer 自动持久化）
    messages: Annotated[list, add_messages]
    usage: Annotated[dict, merge_usage]
