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

    asyncio.run(run())
