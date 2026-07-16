"""Explicit, bounded LangGraph workflow for the recommendation agent."""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass
from typing import Any, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langchain_core.tools import BaseTool
from langgraph.graph import END, START, StateGraph

from app.services.base import RecommendationResult


class AgentExecutionError(RuntimeError):
    """A controlled agent failure whose message is safe for service boundaries."""


class AgentDeadlineError(AgentExecutionError):
    """The overall agent request deadline was exceeded."""


class AgentLoopLimitError(AgentExecutionError):
    """The model requested more tool iterations than allowed."""


class AgentRepeatedToolCallError(AgentExecutionError):
    """The model repeated an identical tool call in one run."""


class AgentFinalizationError(AgentExecutionError):
    """The workflow ended without a valid final model answer."""


@dataclass(frozen=True)
class AgentWorkflowConfig:
    """Hard safety bounds applied to every graph invocation."""

    max_tool_iterations: int = 3
    max_tool_calls_per_iteration: int = 4
    max_tool_result_chars: int = 4000
    deadline_seconds: float = 30.0

    def __post_init__(self) -> None:
        if self.max_tool_iterations < 0:
            raise ValueError("max_tool_iterations must not be negative")
        if self.max_tool_calls_per_iteration < 1:
            raise ValueError("max_tool_calls_per_iteration must be positive")
        if self.max_tool_result_chars < 100:
            raise ValueError("max_tool_result_chars must be at least 100")
        if self.deadline_seconds <= 0:
            raise ValueError("deadline_seconds must be positive")


@dataclass(frozen=True)
class AgentRequest:
    """Provider-neutral request retained in graph state for later tools."""

    system_prompt: str
    user_message: str
    history: tuple[dict[str, str], ...] = ()
    selected_tags: tuple[str, ...] = ()
    steam_profile_summary: str | None = None
    request_id: str | None = None

    def initial_messages(self) -> list[BaseMessage]:
        messages: list[BaseMessage] = [SystemMessage(content=self.system_prompt)]
        for item in self.history:
            role = item.get("role")
            content = item.get("content")
            if not isinstance(content, str) or not content.strip():
                continue
            if role == "user":
                messages.append(HumanMessage(content=content))
            elif role == "assistant":
                messages.append(AIMessage(content=content))
        messages.append(HumanMessage(content=self.user_message))
        return messages


class AgentState(TypedDict):
    """Transient state; it is never persisted as chain-of-thought."""

    request: AgentRequest
    messages: list[BaseMessage]
    model_steps: int
    tool_iterations: int
    seen_tool_calls: list[str]
    deadline_at: float
    final_result: RecommendationResult | None


Finalizer = Callable[[str], Awaitable[RecommendationResult]]


class AgentWorkflow:
    """Model → tools/final → model loop with explicit hard limits."""

    def __init__(
        self,
        model: Any,
        tools: Sequence[BaseTool],
        finalizer: Finalizer,
        config: AgentWorkflowConfig | None = None,
    ) -> None:
        self.config = config or AgentWorkflowConfig()
        self.tools = {tool.name: tool for tool in tools}
        self.finalizer = finalizer
        self.model = model.bind_tools(list(tools)) if tools else model
        self.graph = self._build_graph()

    def _build_graph(self) -> Any:
        builder = StateGraph(AgentState)
        builder.add_node("model", self._model_node)
        builder.add_node("tools", self._tools_node)
        builder.add_node("finalize", self._finalize_node)
        builder.add_edge(START, "model")
        builder.add_conditional_edges(
            "model",
            self._route_after_model,
            {"tools": "tools", "finalize": "finalize"},
        )
        builder.add_edge("tools", "model")
        builder.add_edge("finalize", END)
        return builder.compile()

    def _ensure_deadline(self, state: AgentState) -> None:
        if time.monotonic() >= state["deadline_at"]:
            raise AgentDeadlineError("AI agent deadline exceeded")

    async def _model_node(self, state: AgentState) -> dict[str, Any]:
        self._ensure_deadline(state)
        response = await self.model.ainvoke(state["messages"])
        if not isinstance(response, AIMessage):
            raise AgentExecutionError("AI provider returned an unsupported response")
        return {
            "messages": [*state["messages"], response],
            "model_steps": state["model_steps"] + 1,
        }

    @staticmethod
    def _last_ai_message(state: AgentState) -> AIMessage:
        if not state["messages"] or not isinstance(state["messages"][-1], AIMessage):
            raise AgentFinalizationError("AI agent produced no final response")
        return state["messages"][-1]

    def _route_after_model(self, state: AgentState) -> str:
        return "tools" if self._last_ai_message(state).tool_calls else "finalize"

    @staticmethod
    def _tool_signature(name: str, arguments: dict[str, Any]) -> str:
        serialized = json.dumps(
            arguments,
            ensure_ascii=True,
            sort_keys=True,
            separators=(",", ":"),
            default=str,
        )
        return f"{name}:{serialized}"

    @staticmethod
    def _safe_tool_error(code: str) -> str:
        return json.dumps(
            {
                "ok": False,
                "error": {
                    "code": code,
                    "message": "The tool could not complete the request",
                },
            },
            separators=(",", ":"),
        )

    def _serialize_tool_result(self, result: Any) -> str:
        if isinstance(result, ToolMessage):
            value = result.content
        elif isinstance(result, str):
            value = result
        else:
            value = json.dumps(result, ensure_ascii=False, default=str)
        if not isinstance(value, str):
            value = json.dumps(value, ensure_ascii=False, default=str)
        return value[: self.config.max_tool_result_chars]

    async def _tools_node(self, state: AgentState) -> dict[str, Any]:
        self._ensure_deadline(state)
        if state["tool_iterations"] >= self.config.max_tool_iterations:
            raise AgentLoopLimitError("AI agent tool iteration limit exceeded")

        tool_calls = self._last_ai_message(state).tool_calls
        if len(tool_calls) > self.config.max_tool_calls_per_iteration:
            raise AgentLoopLimitError("AI agent requested too many tools at once")

        seen = set(state["seen_tool_calls"])
        signatures: list[str] = []
        tool_messages: list[ToolMessage] = []
        for call in tool_calls:
            name = str(call.get("name", ""))
            arguments = call.get("args", {})
            signature = self._tool_signature(name, arguments)
            if signature in seen or signature in signatures:
                raise AgentRepeatedToolCallError(
                    "AI agent repeated an identical tool call"
                )
            signatures.append(signature)

            tool = self.tools.get(name)
            if tool is None:
                content = self._safe_tool_error("UNKNOWN_TOOL")
            else:
                try:
                    content = self._serialize_tool_result(
                        await tool.ainvoke(arguments)
                    )
                except Exception:
                    content = self._safe_tool_error("TOOL_ERROR")
            tool_messages.append(
                ToolMessage(
                    content=content,
                    tool_call_id=str(call.get("id", "unknown")),
                    name=name or None,
                )
            )

        return {
            "messages": [*state["messages"], *tool_messages],
            "tool_iterations": state["tool_iterations"] + 1,
            "seen_tool_calls": [*state["seen_tool_calls"], *signatures],
        }

    async def _finalize_node(self, state: AgentState) -> dict[str, Any]:
        self._ensure_deadline(state)
        final_message = self._last_ai_message(state)
        content = final_message.content
        if not isinstance(content, str) or not content.strip():
            raise AgentFinalizationError("AI agent produced an empty final response")
        return {"final_result": await self.finalizer(content)}

    async def run(self, request: AgentRequest) -> RecommendationResult:
        deadline_at = time.monotonic() + self.config.deadline_seconds
        initial: AgentState = {
            "request": request,
            "messages": request.initial_messages(),
            "model_steps": 0,
            "tool_iterations": 0,
            "seen_tool_calls": [],
            "deadline_at": deadline_at,
            "final_result": None,
        }
        recursion_limit = self.config.max_tool_iterations * 2 + 5
        try:
            async with asyncio.timeout(self.config.deadline_seconds):
                state = await self.graph.ainvoke(
                    initial,
                    config={"recursion_limit": recursion_limit},
                )
        except TimeoutError as exc:
            raise AgentDeadlineError("AI agent deadline exceeded") from exc

        result = state.get("final_result")
        if not isinstance(result, RecommendationResult):
            raise AgentFinalizationError("AI agent produced no final result")
        return result
