import asyncio
from typing import Sequence, cast

import grpc
from grpc import aio

from app.tools.grpc_client import (
    JavaToolsClient,
    JavaToolsClientConfig,
    ToolErrorCode,
    tools_pb2,
    tools_pb2_grpc,
)


class FakeJavaToolsServicer(tools_pb2_grpc.JavaToolsServiceServicer):
    def __init__(self) -> None:
        self.metadata: list[dict[str, str]] = []
        self.search_calls = 0
        self.details_calls = 0
        self.search_status: grpc.StatusCode | None = None
        self.details_status: grpc.StatusCode | None = None
        self.delay_seconds = 0.0

    def _capture(self, context: grpc.aio.ServicerContext) -> None:
        metadata = cast(
            Sequence[tuple[str, str]],
            context.invocation_metadata() or (),
        )
        self.metadata.append(dict(metadata))

    async def SearchGames(self, request, context):
        self._capture(context)
        self.search_calls += 1
        if self.delay_seconds:
            await asyncio.sleep(self.delay_seconds)
        if self.search_status is not None:
            await context.abort(self.search_status, "private backend details")
        return tools_pb2.SearchGamesResponse(
            games=[
                tools_pb2.SteamAppResponse(
                    app_id=620,
                    name="Portal 2",
                    genres=["Puzzle", "Co-op"],
                )
            ]
        )

    async def GetSteamAppDetails(self, request, context):
        self._capture(context)
        self.details_calls += 1
        if self.details_status is not None:
            await context.abort(self.details_status, "private backend details")
        return tools_pb2.SteamAppResponse(
            app_id=request.app_id,
            name="Portal 2",
            description="Co-operative puzzle game",
            genres=["Puzzle"],
        )


async def start_server(
    servicer: FakeJavaToolsServicer,
) -> tuple[aio.Server, str]:
    server = aio.server()
    tools_pb2_grpc.add_JavaToolsServiceServicer_to_server(servicer, server)
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    return server, f"127.0.0.1:{port}"


def test_in_process_client_calls_both_tools_and_propagates_request_id() -> None:
    async def run() -> None:
        servicer = FakeJavaToolsServicer()
        server, target = await start_server(servicer)
        client = JavaToolsClient(
            JavaToolsClientConfig(target=target, max_retries=0)
        )
        try:
            search = await client.search_games("Portal", 3, "request-129")
            details = await client.get_steam_app_details(620, "request-129")
        finally:
            await client.close()
            await server.stop(None)

        assert search.ok is True
        assert [game.name for game in search.games] == ["Portal 2"]
        assert details.ok is True
        assert details.game is not None
        assert details.game.description == "Co-operative puzzle game"
        assert servicer.search_calls == 1
        assert servicer.details_calls == 1
        assert [item["x-request-id"] for item in servicer.metadata] == [
            "request-129",
            "request-129",
        ]

    asyncio.run(run())


def test_not_found_is_a_typed_result_without_backend_details() -> None:
    async def run() -> None:
        servicer = FakeJavaToolsServicer()
        servicer.details_status = grpc.StatusCode.NOT_FOUND
        server, target = await start_server(servicer)
        client = JavaToolsClient(
            JavaToolsClientConfig(target=target, max_retries=1)
        )
        try:
            result = await client.get_steam_app_details(999999, "request-404")
        finally:
            await client.close()
            await server.stop(None)

        assert result.ok is False
        assert result.error is not None
        assert result.error.code is ToolErrorCode.NOT_FOUND
        assert "private" not in result.error.message
        assert servicer.details_calls == 1

    asyncio.run(run())


def test_unavailable_is_retried_once_then_returned_as_typed_error() -> None:
    async def run() -> None:
        servicer = FakeJavaToolsServicer()
        servicer.search_status = grpc.StatusCode.UNAVAILABLE
        server, target = await start_server(servicer)
        client = JavaToolsClient(
            JavaToolsClientConfig(
                target=target,
                max_retries=1,
                retry_backoff_seconds=0,
            )
        )
        try:
            result = await client.search_games("Portal", 3, "request-retry")
        finally:
            await client.close()
            await server.stop(None)

        assert result.ok is False
        assert result.error is not None
        assert result.error.code is ToolErrorCode.UNAVAILABLE
        assert servicer.search_calls == 2

    asyncio.run(run())


def test_deadline_is_retried_once_then_returned_as_typed_error() -> None:
    async def run() -> None:
        servicer = FakeJavaToolsServicer()
        servicer.delay_seconds = 0.05
        server, target = await start_server(servicer)
        client = JavaToolsClient(
            JavaToolsClientConfig(
                target=target,
                deadline_seconds=0.01,
                max_retries=1,
                retry_backoff_seconds=0,
            )
        )
        try:
            result = await client.search_games("Portal", 3, "request-timeout")
        finally:
            await client.close()
            await server.stop(None)

        assert result.ok is False
        assert result.error is not None
        assert result.error.code is ToolErrorCode.DEADLINE_EXCEEDED
        assert servicer.search_calls == 2

    asyncio.run(run())
