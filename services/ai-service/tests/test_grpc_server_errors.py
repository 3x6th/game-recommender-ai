import asyncio
from unittest.mock import AsyncMock, Mock

import grpc

from app.grpc_server import PUBLIC_AI_ERROR, GameRecommenderServicer, reco_pb2


def test_grpc_error_does_not_expose_raw_provider_exception() -> None:
    async def run() -> None:
        registry = Mock()
        registry.get_recommendations_with_steam_library = AsyncMock(
            side_effect=RuntimeError("secret provider token and endpoint")
        )
        context = Mock()
        servicer = GameRecommenderServicer(registry)

        response = await servicer.RecommendGames(
            reco_pb2.FullAiContextRequestProto(
                userMessage="find a game",
                maxResults=5,
            ),
            context,
        )

        assert response.success is False
        assert response.message == PUBLIC_AI_ERROR
        assert "secret" not in response.message
        context.set_code.assert_called_once_with(grpc.StatusCode.INTERNAL)
        context.set_details.assert_called_once_with(PUBLIC_AI_ERROR)
        call = registry.get_recommendations_with_steam_library.await_args
        assert call.kwargs["request_id"] is None

    asyncio.run(run())


def test_grpc_request_id_is_forwarded_to_agent_runtime() -> None:
    async def run() -> None:
        registry = Mock()
        registry.get_recommendations_with_steam_library = AsyncMock(
            return_value=Mock(recommendations=[], reply="ok", reasoning="")
        )
        registry.get_active_provider.return_value = "DeepSeekService"
        context = Mock()
        servicer = GameRecommenderServicer(registry)

        response = await servicer.RecommendGames(
            reco_pb2.FullAiContextRequestProto(
                userMessage="find a game",
                maxResults=5,
                requestId="request-from-java",
            ),
            context,
        )

        assert response.success is True
        call = registry.get_recommendations_with_steam_library.await_args
        assert call.kwargs["request_id"] == "request-from-java"

    asyncio.run(run())
