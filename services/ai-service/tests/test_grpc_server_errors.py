import asyncio
import uuid
from unittest.mock import AsyncMock, Mock

from app.grpc_server import PUBLIC_AI_ERROR, GameRecommenderServicer, reco_pb2
from app.observability import current_request_id


def test_grpc_error_does_not_expose_raw_provider_exception() -> None:
    async def run() -> None:
        registry = Mock()
        registry.get_recommendations_with_steam_library = AsyncMock(
            side_effect=RuntimeError("secret provider token and endpoint")
        )
        context = Mock()
        context.invocation_metadata.return_value = ()
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
        context.set_code.assert_not_called()
        context.set_details.assert_not_called()
        call = registry.get_recommendations_with_steam_library.await_args
        uuid.UUID(call.kwargs["request_id"])
        assert current_request_id() is None

    asyncio.run(run())


def test_grpc_request_id_is_forwarded_to_agent_runtime() -> None:
    async def run() -> None:
        registry = Mock()
        registry.get_recommendations_with_steam_library = AsyncMock(
            return_value=Mock(recommendations=[], reply="ok", reasoning="")
        )
        registry.get_active_provider.return_value = "DeepSeekService"
        context = Mock()
        context.invocation_metadata.return_value = ()
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


def test_grpc_metadata_request_id_wins_and_is_bound_during_agent_call() -> None:
    async def run() -> None:
        seen_context: list[str | None] = []

        async def recommendations(**kwargs):
            seen_context.append(current_request_id())
            return Mock(recommendations=[], reply="ok", reasoning="")

        registry = Mock()
        registry.get_recommendations_with_steam_library = AsyncMock(
            side_effect=recommendations
        )
        registry.get_active_provider.return_value = "DeepSeekService"
        context = Mock()
        context.invocation_metadata.return_value = (
            ("x-request-id", "metadata-trace-id"),
        )
        servicer = GameRecommenderServicer(registry)

        response = await servicer.RecommendGames(
            reco_pb2.FullAiContextRequestProto(
                userMessage="find a game",
                maxResults=5,
                requestId="protobuf-request-id",
            ),
            context,
        )

        assert response.success is True
        call = registry.get_recommendations_with_steam_library.await_args
        assert call.kwargs["request_id"] == "metadata-trace-id"
        assert seen_context == ["metadata-trace-id"]
        assert current_request_id() is None

    asyncio.run(run())
