"""Explicit, bounded LangGraph workflow for the recommendation agent."""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
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

from app.observability import AI_METRICS, AIMetrics
from app.services.base import RecommendationResult


logger = logging.getLogger(__name__)


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
    run_id: str
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
        metrics: AIMetrics | None = None,
        provider: str = "unknown",
        model_name: str = "unknown",
    ) -> None:
        self.config = config or AgentWorkflowConfig()
        self.metrics = metrics or AI_METRICS
        self.provider = provider
        self.model_name = model_name
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

    @staticmethod
    def _safe_log_value(value: str | None) -> str:
        if not value:
            return "-"
        return "_".join(value.split())[:128] or "-"

    @staticmethod
    def _message_chars(messages: Sequence[BaseMessage]) -> int:
        total = 0
        for message in messages:
            content = message.content
            if isinstance(content, str):
                total += len(content)
            else:
                total += len(json.dumps(content, ensure_ascii=False, default=str))
        return total

    @staticmethod
    def _error_outcome(error: BaseException) -> str:
        if isinstance(error, AgentDeadlineError):
            return "deadline"
        if isinstance(error, AgentLoopLimitError):
            return "loop_limit"
        if isinstance(error, AgentRepeatedToolCallError):
            return "repeated_tool_call"
        if isinstance(error, AgentFinalizationError):
            return "finalization_error"
        return "error"

    def _log_event(
        self,
        event: str,
        state: AgentState,
        *,
        node: str,
        outcome: str,
        duration_seconds: float,
        iteration: int = 0,
        tool: str | None = None,
        input_chars: int = 0,
        output_chars: int = 0,
    ) -> None:
        request = state["request"]
        logger.info(
            "agent_event event=%s request_id=%s run_id=%s provider=%s model=%s "
            "node=%s iteration=%d tool=%s status=%s latency_ms=%.3f "
            "input_chars=%d output_chars=%d",
            event,
            self._safe_log_value(request.request_id),
            state["run_id"],
            self._safe_log_value(self.provider),
            self._safe_log_value(self.model_name),
            node,
            iteration,
            self._safe_log_value(tool),
            outcome,
            duration_seconds * 1000,
            max(input_chars, 0),
            max(output_chars, 0),
        )

    async def _model_node(self, state: AgentState) -> dict[str, Any]:
        step_started = time.monotonic()
        call_started: float | None = None
        outcome = "error"
        output_chars = 0
        try:
            self._ensure_deadline(state)
            call_started = time.monotonic()
            response = await self.model.ainvoke(state["messages"])
            if not isinstance(response, AIMessage):
                raise AgentExecutionError(
                    "AI provider returned an unsupported response"
                )
            output_chars = self._message_chars([response])
            usage = getattr(response, "usage_metadata", None)
            response_metadata = getattr(response, "response_metadata", None)
            self.metrics.record_usage(
                self.provider,
                self.model_name,
                usage if isinstance(usage, dict) else None,
                response_metadata if isinstance(response_metadata, dict) else None,
            )
            outcome = "success"
            return {
                "messages": [*state["messages"], response],
                "model_steps": state["model_steps"] + 1,
            }
        except Exception as error:
            outcome = self._error_outcome(error)
            raise
        finally:
            finished = time.monotonic()
            if call_started is not None:
                self.metrics.observe_llm(
                    self.provider,
                    self.model_name,
                    outcome,
                    finished - call_started,
                )
            self.metrics.record_agent_step("model", outcome)
            self._log_event(
                "agent_step",
                state,
                node="model",
                outcome=outcome,
                duration_seconds=finished - step_started,
                iteration=state["model_steps"] + 1,
                input_chars=self._message_chars(state["messages"]),
                output_chars=output_chars,
            )

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
        node_started = time.monotonic()
        node_outcome = "error"
        try:
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

                tool_started = time.monotonic()
                tool_outcome = "error"
                content = ""
                tool = self.tools.get(name)
                try:
                    if tool is None:
                        tool_outcome = "unknown_tool"
                        content = self._safe_tool_error("UNKNOWN_TOOL")
                    else:
                        try:
                            content = self._serialize_tool_result(
                                await tool.ainvoke(arguments)
                            )
                            tool_outcome = "success"
                        except Exception:
                            tool_outcome = "error"
                            content = self._safe_tool_error("TOOL_ERROR")
                except asyncio.CancelledError:
                    tool_outcome = "deadline"
                    raise
                finally:
                    tool_duration = time.monotonic() - tool_started
                    metric_tool = name if tool is not None else "unknown"
                    self.metrics.observe_tool(
                        metric_tool,
                        tool_outcome,
                        tool_duration,
                    )
                    self._log_event(
                        "tool_call",
                        state,
                        node="tools",
                        tool=name or "unknown",
                        outcome=tool_outcome,
                        duration_seconds=tool_duration,
                        iteration=state["tool_iterations"] + 1,
                        input_chars=len(
                            json.dumps(arguments, ensure_ascii=False, default=str)
                        ),
                        output_chars=len(content),
                    )
                tool_messages.append(
                    ToolMessage(
                        content=content,
                        tool_call_id=str(call.get("id", "unknown")),
                        name=name or None,
                    )
                )

            node_outcome = "success"
            return {
                "messages": [*state["messages"], *tool_messages],
                "tool_iterations": state["tool_iterations"] + 1,
                "seen_tool_calls": [*state["seen_tool_calls"], *signatures],
            }
        except Exception as error:
            node_outcome = self._error_outcome(error)
            raise
        finally:
            node_duration = time.monotonic() - node_started
            self.metrics.record_agent_step("tools", node_outcome)
            self._log_event(
                "agent_step",
                state,
                node="tools",
                outcome=node_outcome,
                duration_seconds=node_duration,
                iteration=state["tool_iterations"] + 1,
            )

    async def _finalize_node(self, state: AgentState) -> dict[str, Any]:
        started = time.monotonic()
        outcome = "error"
        input_chars = 0
        output_chars = 0
        try:
            self._ensure_deadline(state)
            final_message = self._last_ai_message(state)
            content = final_message.content
            if not isinstance(content, str) or not content.strip():
                raise AgentFinalizationError("AI agent produced an empty final response")
            input_chars = len(content)
            result = await self.finalizer(content)
            output_chars = len(result.reply or "") + len(result.reasoning or "")
            outcome = "success"
            return {"final_result": result}
        except Exception as error:
            outcome = self._error_outcome(error)
            raise
        finally:
            duration = time.monotonic() - started
            self.metrics.record_agent_step("finalize", outcome)
            self._log_event(
                "agent_step",
                state,
                node="finalize",
                outcome=outcome,
                duration_seconds=duration,
                iteration=state["model_steps"],
                input_chars=input_chars,
                output_chars=output_chars,
            )

    async def run(self, request: AgentRequest) -> RecommendationResult:
        run_started = time.monotonic()
        deadline_at = time.monotonic() + self.config.deadline_seconds
        initial: AgentState = {
            "request": request,
            "messages": request.initial_messages(),
            "model_steps": 0,
            "tool_iterations": 0,
            "seen_tool_calls": [],
            "deadline_at": deadline_at,
            "run_id": str(uuid.uuid4()),
            "final_result": None,
        }
        recursion_limit = self.config.max_tool_iterations * 2 + 5
        outcome = "error"
        result: RecommendationResult | None = None
        try:
            async with asyncio.timeout(self.config.deadline_seconds):
                state = await self.graph.ainvoke(
                    initial,
                    config={"recursion_limit": recursion_limit},
                )
            result = state.get("final_result")
            if not isinstance(result, RecommendationResult):
                raise AgentFinalizationError("AI agent produced no final result")
            outcome = "success"
        except TimeoutError as exc:
            outcome = "deadline"
            self.metrics.record_agent_limit("deadline")
            raise AgentDeadlineError("AI agent deadline exceeded") from exc
        except AgentDeadlineError:
            outcome = "deadline"
            self.metrics.record_agent_limit("deadline")
            raise
        except AgentLoopLimitError:
            outcome = "loop_limit"
            self.metrics.record_agent_limit("loop_limit")
            raise
        except AgentRepeatedToolCallError:
            outcome = "repeated_tool_call"
            self.metrics.record_agent_limit("repeated_tool_call")
            raise
        except Exception as error:
            outcome = self._error_outcome(error)
            raise
        finally:
            self._log_event(
                "agent_run",
                initial,
                node="graph",
                outcome=outcome,
                duration_seconds=time.monotonic() - run_started,
                input_chars=len(request.user_message),
            )

        if result is None:
            raise AgentFinalizationError("AI agent produced no final result")
        return result
