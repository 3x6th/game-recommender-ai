"""Deterministic production-servicer harness for Java↔Python contract tests."""

from __future__ import annotations

import asyncio
import json
import logging
import os
import signal
from collections.abc import Sequence
from typing import Any

from grpc import aio
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.tools import BaseTool

from app.grpc_server import GameRecommenderServicer, reco_pb2_grpc
from app.services.base import RecommendationResult
from app.services.deepseek_service import DeepSeekService
from app.tools import JavaToolsClient, create_java_tools


class ContractChatModel:
    """Script model selected by a request prefix, never by live services."""

    def __init__(self, scenario: str) -> None:
        self.scenario = scenario
        self.calls = 0

    def bind_tools(self, tools: Sequence[BaseTool]) -> "ContractChatModel":
        return self

    @staticmethod
    def _json(
        reply: str = "",
        reasoning: str = "",
        recommendations: list[dict[str, Any]] | None = None,
    ) -> AIMessage:
        return AIMessage(
            content=json.dumps(
                {
                    "reply": reply,
                    "reasoning": reasoning,
                    "recommendations": recommendations or [],
                },
                ensure_ascii=False,
            )
        )

    @staticmethod
    def _tool_call(name: str, call_id: str, **arguments: Any) -> AIMessage:
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

    async def ainvoke(self, messages: list[BaseMessage]) -> AIMessage:
        call = self.calls
        self.calls += 1

        if self.scenario == "contract:controlled-error":
            raise RuntimeError("private contract provider failure")
        if self.scenario == "contract:loop-limit":
            return self._tool_call(
                "search_games",
                f"loop-{call}",
                query=f"loop-{call}",
                limit=1,
            )
        if self.scenario == "contract:repair":
            if call == 0:
                return AIMessage(content="repair this conversational answer")
            return self._json(reply="Repaired reply")
        if self.scenario == "contract:text":
            history = next(
                (
                    str(message.content)
                    for message in messages
                    if message.type == "ai" and message.content
                ),
                "missing",
            )
            return self._json(reply=f"History preserved: {history}")
        if self.scenario == "contract:tool-chain":
            if call == 0:
                return self._tool_call(
                    "search_games",
                    "search-factorio",
                    query="Factorio",
                    limit=2,
                )
            if call == 1:
                return self._tool_call(
                    "steam_app_details",
                    "details-factorio",
                    app_id=427520,
                )
            return self._json(
                reasoning="Verified by Java tools",
                recommendations=[
                    {
                        "title": "Factorio",
                        "genre": "Automation Strategy",
                        "description": "Build automated factories",
                        "why_recommended": "Verified catalog match",
                        "platforms": ["PC"],
                        "rating": 9.0,
                        "release_year": "2020",
                    }
                ],
            )
        if self.scenario == "contract:tool-not-found":
            if call == 0:
                return self._tool_call(
                    "steam_app_details",
                    "details-missing",
                    app_id=999999999,
                )
            return self._json(reply="Steam app was not found")
        if self.scenario == "contract:tool-unavailable":
            if call == 0:
                return self._tool_call(
                    "steam_app_details",
                    "details-unavailable",
                    app_id=440,
                )
            return self._json(reply="Catalog is temporarily unavailable")
        if self.scenario == "contract:tool-deadline":
            if call == 0:
                return self._tool_call(
                    "search_games",
                    "search-slow",
                    query="slow",
                    limit=1,
                )
            return self._json(reply="Catalog deadline exceeded")

        return self._json(
            reply="Cards ready",
            reasoning="Contract reasoning",
            recommendations=[
                {
                    "title": "Portal 2",
                    "genre": "Puzzle",
                    "description": "Co-operative puzzle game",
                    "why_recommended": "Matches the conversation",
                    "platforms": ["PC"],
                    "rating": 9.5,
                    "release_year": "2011",
                }
            ],
        )


class ContractRegistry:
    """Creates one production DeepSeek workflow per deterministic request."""

    def __init__(self) -> None:
        self.tools_client = JavaToolsClient()

    async def get_recommendations_with_steam_library(
        self,
        user_message: str,
        selected_tags: list[str],
        steam_library: str | None,
        max_recommendations: int = 5,
        history: list[dict[str, str]] | None = None,
        request_id: str | None = None,
    ) -> RecommendationResult:
        service = DeepSeekService(
            api_key="contract-test",
            chat_model=ContractChatModel(user_message),
            agent_tool_factory=lambda hidden_request_id: create_java_tools(
                self.tools_client,
                hidden_request_id,
            ),
        )
        return await service.get_recommendations_with_steam_library(
            user_message=user_message,
            selected_tags=selected_tags,
            steam_library=steam_library,
            max_recommendations=max_recommendations,
            history=history,
            request_id=request_id,
        )

    @staticmethod
    def get_active_provider() -> str:
        return "DeepSeekContractModel"

    async def close(self) -> None:
        await self.tools_client.close()


async def serve() -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    port = int(os.getenv("CONTRACT_AI_GRPC_PORT", "19090"))
    registry = ContractRegistry()
    server = aio.server()
    reco_pb2_grpc.add_GameRecommenderServiceServicer_to_server(
        GameRecommenderServicer(registry),  # type: ignore[arg-type]
        server,
    )
    server.add_insecure_port(f"127.0.0.1:{port}")
    await server.start()
    print(f"CONTRACT_SERVER_READY:{port}", flush=True)

    stopped = asyncio.Event()
    loop = asyncio.get_running_loop()
    for signame in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(signame, stopped.set)
    try:
        await stopped.wait()
    finally:
        await server.stop(grace=1)
        await registry.close()


if __name__ == "__main__":
    asyncio.run(serve())
