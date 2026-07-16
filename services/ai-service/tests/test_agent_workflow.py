import asyncio
from collections.abc import Sequence
from typing import Any

import pytest
from langchain_core.messages import AIMessage, BaseMessage, ToolMessage
from langchain_core.tools import BaseTool, tool

from app.agent.workflow import (
    AgentDeadlineError,
    AgentLoopLimitError,
    AgentRepeatedToolCallError,
    AgentRequest,
    AgentWorkflow,
    AgentWorkflowConfig,
)
from app.services.base import RecommendationResult


class ScriptedChatModel:
    def __init__(
        self,
        responses: Sequence[AIMessage],
        delay_seconds: float = 0.0,
    ) -> None:
        self.responses = list(responses)
        self.delay_seconds = delay_seconds
        self.calls: list[list[BaseMessage]] = []
        self.bound_tools: list[str] = []

    def bind_tools(self, tools: Sequence[BaseTool]) -> "ScriptedChatModel":
        self.bound_tools = [item.name for item in tools]
        return self

    async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
        self.calls.append(list(messages))
        if self.delay_seconds:
            await asyncio.sleep(self.delay_seconds)
        if not self.responses:
            raise RuntimeError("no scripted response")
        return self.responses.pop(0)


async def reply_finalizer(content: str) -> RecommendationResult:
    return RecommendationResult(reply=content)


def request() -> AgentRequest:
    return AgentRequest(
        system_prompt="system rules",
        user_message="find a game",
        history=(
            {"role": "user", "content": "I like puzzles"},
            {"role": "assistant", "content": "Try Portal 2"},
        ),
        selected_tags=("Puzzle",),
        steam_profile_summary='{"topByPlaytime": []}',
    )


def tool_call(name: str, call_id: str, **arguments: Any) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {
                "name": name,
                "args": arguments,
                "id": call_id,
                "type": "tool_call",
            }
        ],
    )


def test_no_tool_request_finishes_after_one_model_step() -> None:
    async def run() -> None:
        model = ScriptedChatModel([AIMessage(content="final answer")])
        workflow = AgentWorkflow(model, [], reply_finalizer)

        result = await workflow.run(request())

        assert result.reply == "final answer"
        assert len(model.calls) == 1
        assert [message.type for message in model.calls[0]] == [
            "system",
            "human",
            "ai",
            "human",
        ]

    asyncio.run(run())


def test_one_tool_iteration_returns_result_to_model_and_finalizes() -> None:
    @tool
    async def search_games(query: str) -> dict[str, Any]:
        """Search the game catalog by title."""
        return {"games": [{"app_id": 620, "name": "Portal 2"}], "query": query}

    async def run() -> None:
        model = ScriptedChatModel(
            [
                tool_call("search_games", "call-1", query="Portal"),
                AIMessage(content="Portal 2 found"),
            ]
        )
        workflow = AgentWorkflow(model, [search_games], reply_finalizer)

        result = await workflow.run(request())

        assert result.reply == "Portal 2 found"
        assert model.bound_tools == ["search_games"]
        assert len(model.calls) == 2
        tool_message = model.calls[1][-1]
        assert isinstance(tool_message, ToolMessage)
        assert "Portal 2" in str(tool_message.content)

    asyncio.run(run())


def test_tool_exception_becomes_safe_tool_result_instead_of_escaping() -> None:
    @tool
    async def broken_tool(query: str) -> str:
        """A tool used to verify controlled failures."""
        raise RuntimeError(f"secret backend failure for {query}")

    async def run() -> None:
        model = ScriptedChatModel(
            [
                tool_call("broken_tool", "call-1", query="secret-query"),
                AIMessage(content="Catalog is temporarily unavailable"),
            ]
        )
        workflow = AgentWorkflow(model, [broken_tool], reply_finalizer)

        result = await workflow.run(request())

        tool_message = model.calls[1][-1]
        assert isinstance(tool_message, ToolMessage)
        assert "TOOL_ERROR" in str(tool_message.content)
        assert "secret" not in str(tool_message.content)
        assert result.reply == "Catalog is temporarily unavailable"

    asyncio.run(run())


def test_repeated_identical_tool_call_is_stopped() -> None:
    @tool
    async def search_games(query: str) -> str:
        """Search the game catalog by title."""
        return query

    async def run() -> None:
        model = ScriptedChatModel(
            [
                tool_call("search_games", "call-1", query="Portal"),
                tool_call("search_games", "call-2", query="Portal"),
            ]
        )
        workflow = AgentWorkflow(model, [search_games], reply_finalizer)

        with pytest.raises(AgentRepeatedToolCallError, match="repeated"):
            await workflow.run(request())

    asyncio.run(run())


def test_tool_iteration_limit_is_enforced() -> None:
    @tool
    async def search_games(query: str) -> str:
        """Search the game catalog by title."""
        return query

    async def run() -> None:
        model = ScriptedChatModel(
            [
                tool_call("search_games", "call-1", query="one"),
                tool_call("search_games", "call-2", query="two"),
                tool_call("search_games", "call-3", query="three"),
            ]
        )
        workflow = AgentWorkflow(
            model,
            [search_games],
            reply_finalizer,
            AgentWorkflowConfig(max_tool_iterations=2),
        )

        with pytest.raises(AgentLoopLimitError, match="limit"):
            await workflow.run(request())

    asyncio.run(run())


def test_tool_call_limit_per_iteration_is_enforced() -> None:
    @tool
    async def search_games(query: str) -> str:
        """Search the game catalog by title."""
        return query

    async def run() -> None:
        model = ScriptedChatModel(
            [
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "search_games",
                            "args": {"query": "one"},
                            "id": "call-1",
                            "type": "tool_call",
                        },
                        {
                            "name": "search_games",
                            "args": {"query": "two"},
                            "id": "call-2",
                            "type": "tool_call",
                        },
                    ],
                )
            ]
        )
        workflow = AgentWorkflow(
            model,
            [search_games],
            reply_finalizer,
            AgentWorkflowConfig(max_tool_calls_per_iteration=1),
        )

        with pytest.raises(AgentLoopLimitError, match="too many tools"):
            await workflow.run(request())

    asyncio.run(run())


def test_default_tool_call_limit_supports_one_lookup_per_recommendation() -> None:
    @tool
    async def search_games(query: str) -> str:
        """Search the game catalog by title."""
        return query

    async def run() -> None:
        calls = [
            {
                "name": "search_games",
                "args": {"query": f"game-{index}"},
                "id": f"call-{index}",
                "type": "tool_call",
            }
            for index in range(5)
        ]
        model = ScriptedChatModel(
            [AIMessage(content="", tool_calls=calls), AIMessage(content="final answer")]
        )
        workflow = AgentWorkflow(model, [search_games], reply_finalizer)

        result = await workflow.run(request())

        assert result.reply == "final answer"
        assert len(model.calls) == 2
        assert all(isinstance(message, ToolMessage) for message in model.calls[1][-5:])

    asyncio.run(run())


def test_tool_result_is_truncated_before_returning_to_model() -> None:
    @tool
    async def large_result(query: str) -> str:
        """Return a deliberately large catalog payload."""
        return query * 200

    async def run() -> None:
        model = ScriptedChatModel(
            [
                tool_call("large_result", "call-1", query="catalog"),
                AIMessage(content="final answer"),
            ]
        )
        workflow = AgentWorkflow(
            model,
            [large_result],
            reply_finalizer,
            AgentWorkflowConfig(max_tool_result_chars=100),
        )

        await workflow.run(request())

        tool_message = model.calls[1][-1]
        assert isinstance(tool_message, ToolMessage)
        assert len(str(tool_message.content)) == 100

    asyncio.run(run())


def test_overall_deadline_cancels_slow_model() -> None:
    async def run() -> None:
        model = ScriptedChatModel(
            [AIMessage(content="too late")],
            delay_seconds=0.05,
        )
        workflow = AgentWorkflow(
            model,
            [],
            reply_finalizer,
            AgentWorkflowConfig(deadline_seconds=0.01),
        )

        with pytest.raises(AgentDeadlineError, match="deadline"):
            await workflow.run(request())

    asyncio.run(run())
