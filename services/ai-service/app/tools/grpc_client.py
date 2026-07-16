"""Async client for the Java-owned internal game tools API."""

from __future__ import annotations

import asyncio
import logging
import os
import sys
import time
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Awaitable, Callable, TypeVar

import grpc
from grpc import aio
from pydantic import BaseModel, ConfigDict, Field


PROTO_DIR = Path(__file__).resolve().parents[2] / "proto"
if str(PROTO_DIR) not in sys.path:
    sys.path.insert(0, str(PROTO_DIR))

import tools_pb2  # type: ignore[import-not-found]  # noqa: E402
import tools_pb2_grpc  # type: ignore[import-not-found]  # noqa: E402


logger = logging.getLogger(__name__)

ResponseT = TypeVar("ResponseT")


class ToolErrorCode(str, Enum):
    NOT_FOUND = "NOT_FOUND"
    UNAVAILABLE = "UNAVAILABLE"
    DEADLINE_EXCEEDED = "DEADLINE_EXCEEDED"
    INTERNAL = "INTERNAL"


class ToolError(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: ToolErrorCode
    message: str = "The internal game catalog could not complete the request"


class SteamApp(BaseModel):
    model_config = ConfigDict(extra="forbid")

    app_id: int = Field(gt=0)
    name: str
    description: str = ""
    genres: list[str] = Field(default_factory=list)


class SearchGamesResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ok: bool
    games: list[SteamApp] = Field(default_factory=list)
    error: ToolError | None = None


class SteamAppDetailsResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ok: bool
    game: SteamApp | None = None
    error: ToolError | None = None


@dataclass(frozen=True)
class JavaToolsClientConfig:
    target: str = "localhost:9091"
    deadline_seconds: float = 2.5
    max_retries: int = 1
    retry_backoff_seconds: float = 0.1

    def __post_init__(self) -> None:
        if not self.target.strip():
            raise ValueError("Java tools gRPC target must not be empty")
        if self.deadline_seconds <= 0:
            raise ValueError("Java tools deadline must be positive")
        if self.max_retries not in {0, 1}:
            raise ValueError("Java tools supports at most one retry")
        if self.retry_backoff_seconds < 0:
            raise ValueError("Java tools retry backoff must not be negative")

    @classmethod
    def from_env(cls) -> "JavaToolsClientConfig":
        return cls(
            target=os.getenv("JAVA_TOOLS_GRPC_TARGET", "localhost:9091"),
            deadline_seconds=float(
                os.getenv("JAVA_TOOLS_DEADLINE_SECONDS", "2.5")
            ),
            max_retries=int(os.getenv("JAVA_TOOLS_MAX_RETRIES", "1")),
            retry_backoff_seconds=(
                float(os.getenv("JAVA_TOOLS_RETRY_BACKOFF_MS", "100")) / 1000
            ),
        )


class JavaToolsClient:
    """Lazy reusable grpc.aio client with bounded transient retries."""

    _TRANSIENT_CODES = {
        grpc.StatusCode.UNAVAILABLE,
        grpc.StatusCode.DEADLINE_EXCEEDED,
    }

    def __init__(
        self,
        config: JavaToolsClientConfig | None = None,
        stub: Any | None = None,
    ) -> None:
        self.config = config or JavaToolsClientConfig.from_env()
        self._channel: aio.Channel | None = None
        self._stub = stub

    def _ensure_stub(self) -> Any:
        if self._stub is None:
            self._channel = aio.insecure_channel(self.config.target)
            self._stub = tools_pb2_grpc.JavaToolsServiceStub(self._channel)
        return self._stub

    @staticmethod
    def _metadata(request_id: str | None) -> tuple[tuple[str, str], ...]:
        safe_request_id = request_id.strip() if request_id else "unknown"
        return (("x-request-id", safe_request_id or "unknown"),)

    @staticmethod
    def _error_code(status: grpc.StatusCode) -> ToolErrorCode:
        return {
            grpc.StatusCode.NOT_FOUND: ToolErrorCode.NOT_FOUND,
            grpc.StatusCode.UNAVAILABLE: ToolErrorCode.UNAVAILABLE,
            grpc.StatusCode.DEADLINE_EXCEEDED: ToolErrorCode.DEADLINE_EXCEEDED,
        }.get(status, ToolErrorCode.INTERNAL)

    @staticmethod
    def _log_completion(
        tool_name: str,
        status: str,
        started: float,
        attempts: int,
    ) -> None:
        logger.info(
            "Java tool completed tool=%s status=%s latency_ms=%.1f attempts=%d",
            tool_name,
            status,
            (time.perf_counter() - started) * 1000,
            attempts,
        )

    async def _call(
        self,
        tool_name: str,
        rpc: Callable[..., Awaitable[ResponseT]],
        request: Any,
        request_id: str | None,
    ) -> tuple[ResponseT | None, ToolError | None]:
        started = time.perf_counter()
        attempts = 0
        status = "INTERNAL"
        for attempt in range(self.config.max_retries + 1):
            attempts = attempt + 1
            try:
                response = await rpc(
                    request,
                    timeout=self.config.deadline_seconds,
                    metadata=self._metadata(request_id),
                )
                status = "OK"
                self._log_completion(tool_name, status, started, attempts)
                return response, None
            except grpc.RpcError as error:
                grpc_status = error.code()
                status = grpc_status.name
                if (
                    grpc_status in self._TRANSIENT_CODES
                    and attempt < self.config.max_retries
                ):
                    if self.config.retry_backoff_seconds:
                        await asyncio.sleep(self.config.retry_backoff_seconds)
                    continue
                self._log_completion(tool_name, status, started, attempts)
                return None, ToolError(code=self._error_code(grpc_status))
            except Exception:
                status = "INTERNAL"
                self._log_completion(tool_name, status, started, attempts)
                return None, ToolError(code=ToolErrorCode.INTERNAL)
        return None, ToolError(code=ToolErrorCode.INTERNAL)

    @staticmethod
    def _to_game(message: Any) -> SteamApp:
        return SteamApp(
            app_id=message.app_id,
            name=message.name,
            description=message.description,
            genres=list(message.genres),
        )

    async def search_games(
        self,
        query: str,
        limit: int,
        request_id: str | None,
    ) -> SearchGamesResult:
        stub = self._ensure_stub()
        response, error = await self._call(
            "search_games",
            stub.SearchGames,
            tools_pb2.SearchGamesRequest(query=query, limit=limit),
            request_id,
        )
        if response is None:
            return SearchGamesResult(ok=False, error=error)
        return SearchGamesResult(
            ok=True,
            games=[self._to_game(game) for game in response.games],
        )

    async def get_steam_app_details(
        self,
        app_id: int,
        request_id: str | None,
    ) -> SteamAppDetailsResult:
        stub = self._ensure_stub()
        response, error = await self._call(
            "steam_app_details",
            stub.GetSteamAppDetails,
            tools_pb2.SteamAppRequest(app_id=app_id),
            request_id,
        )
        if response is None:
            return SteamAppDetailsResult(ok=False, error=error)
        return SteamAppDetailsResult(ok=True, game=self._to_game(response))

    async def close(self) -> None:
        if self._channel is not None:
            await self._channel.close()
            self._channel = None
            self._stub = None
