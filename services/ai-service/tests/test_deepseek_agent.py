import asyncio
from collections.abc import Sequence

import pytest
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.tools import BaseTool, tool

from app.services.deepseek_service import DeepSeekRequestError, DeepSeekService


class AgentModel:
    def __init__(self, responses: Sequence[AIMessage]) -> None:
        self.responses = list(responses)
        self.bound_tools: list[str] = []
        self.calls: list[list[BaseMessage]] = []

    def bind_tools(self, tools: Sequence[BaseTool]) -> "AgentModel":
        self.bound_tools = [item.name for item in tools]
        return self

    async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
        self.calls.append(list(messages))
        return self.responses.pop(0)


def test_deepseek_service_runs_tool_loop_then_existing_output_guard() -> None:
    @tool
    async def search_games(query: str) -> dict[str, object]:
        """Search games by title in the verified internal catalog."""
        return {"query": query, "games": [{"app_id": 620, "name": "Portal 2"}]}

    async def run() -> None:
        model = AgentModel(
            [
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "search_games",
                            "args": {"query": "Portal"},
                            "id": "call-1",
                            "type": "tool_call",
                        }
                    ],
                ),
                AIMessage(
                    content=(
                        '{"reply":"","reasoning":"Found in the internal catalog",'
                        '"recommendations":[{"title":"Portal 2","genre":"Puzzle"}]}'
                    )
                ),
            ]
        )
        service = DeepSeekService(
            api_key="test-key",
            chat_model=model,
            agent_tools=[search_games],
        )

        result = await service.get_recommendations_with_steam_library(
            user_message="Find Portal",
            selected_tags=["Puzzle"],
            steam_library=None,
        )

        assert model.bound_tools == ["search_games"]
        assert len(model.calls) == 2
        assert [item["title"] for item in result.recommendations] == ["Portal 2"]
        assert result.reasoning == "Found in the internal catalog"

    asyncio.run(run())


def test_raw_provider_exception_is_replaced_with_safe_service_error() -> None:
    class BrokenModel:
        async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
            raise RuntimeError("Authorization secret-token leaked by SDK")

    async def run() -> None:
        service = DeepSeekService(api_key="test-key", chat_model=BrokenModel())

        with pytest.raises(DeepSeekRequestError) as captured:
            await service.get_recommendations_with_steam_library(
                user_message="Find a game",
                selected_tags=[],
                steam_library=None,
            )

        assert str(captured.value) == "DeepSeek agent request failed"
        assert "secret-token" not in str(captured.value)

    asyncio.run(run())
