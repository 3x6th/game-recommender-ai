import asyncio
import os
from collections.abc import Sequence
from unittest.mock import patch

import pytest
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.tools import BaseTool, tool

from app.services.deepseek_service import DeepSeekRequestError, DeepSeekService
from app.tools.grpc_client import (
    SearchGamesResult,
    SteamApp,
    SteamAppDetailsResult,
)
from app.tools.langchain_tools import create_java_tools


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


def test_reasoner_model_is_rejected_when_agent_tools_are_configured() -> None:
    with patch.dict(os.environ, {"DEEPSEEK_MODEL": "deepseek-reasoner"}):
        with pytest.raises(ValueError, match="does not support"):
            DeepSeekService(
                api_key="test-key",
                chat_model=AgentModel([]),
                agent_tool_factory=lambda request_id: (),
            )


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


def test_service_factory_runs_both_java_tools_with_hidden_request_id() -> None:
    class FakeClient:
        def __init__(self) -> None:
            self.calls: list[tuple[str, str | None]] = []

        async def search_games(
            self,
            query: str,
            limit: int,
            request_id: str | None,
        ) -> SearchGamesResult:
            self.calls.append(("search_games", request_id))
            return SearchGamesResult(
                ok=True,
                games=[SteamApp(app_id=620, name="Portal 2")],
            )

        async def get_steam_app_details(
            self,
            app_id: int,
            request_id: str | None,
        ) -> SteamAppDetailsResult:
            self.calls.append(("steam_app_details", request_id))
            return SteamAppDetailsResult(
                ok=True,
                game=SteamApp(
                    app_id=app_id,
                    name="Portal 2",
                    genres=["Puzzle"],
                ),
            )

    async def run() -> None:
        model = AgentModel(
            [
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "search_games",
                            "args": {"query": "Portal", "limit": 2},
                            "id": "call-search",
                            "type": "tool_call",
                        },
                        {
                            "name": "steam_app_details",
                            "args": {"app_id": 620},
                            "id": "call-details",
                            "type": "tool_call",
                        },
                    ],
                ),
                AIMessage(
                    content=(
                        '{"reply":"","reasoning":"Verified with Java tools",'
                        '"recommendations":[{"title":"Portal 2","genre":"Puzzle"}]}'
                    )
                ),
            ]
        )
        client = FakeClient()
        service = DeepSeekService(
            api_key="test-key",
            chat_model=model,
            agent_tool_factory=lambda request_id: create_java_tools(
                client,  # type: ignore[arg-type]
                request_id,
            ),
        )

        result = await service.get_recommendations_with_steam_library(
            user_message="Verify Portal 2",
            selected_tags=["Puzzle"],
            steam_library=None,
            request_id="request-hidden-129",
        )

        assert model.bound_tools == ["search_games", "steam_app_details"]
        assert client.calls == [
            ("search_games", "request-hidden-129"),
            ("steam_app_details", "request-hidden-129"),
        ]
        assert result.recommendations[0]["title"] == "Portal 2"

    asyncio.run(run())
