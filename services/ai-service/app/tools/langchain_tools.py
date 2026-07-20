"""LangChain tool schemas backed by the Java gRPC tools client."""

from __future__ import annotations

from langchain_core.tools import BaseTool, tool
from pydantic import BaseModel, ConfigDict, Field

from app.tools.grpc_client import JavaToolsClient


class SearchGamesInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    query: str = Field(
        min_length=2,
        max_length=120,
        description="Part or all of a game title, without user profile data",
    )
    limit: int = Field(
        default=5,
        ge=1,
        le=20,
        description="Maximum number of catalog matches",
    )


class SteamAppDetailsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    app_id: int = Field(
        gt=0,
        le=2_147_483_647,
        description="Positive Steam application id returned by search_games",
    )


def create_java_tools(
    client: JavaToolsClient,
    request_id: str | None,
) -> tuple[BaseTool, ...]:
    """Create per-request tools while keeping requestId hidden from the model."""

    @tool("search_games", args_schema=SearchGamesInput)
    async def search_games(query: str, limit: int = 5) -> dict[str, object]:
        """Search the verified internal game catalog by title before recommending uncertain game names."""

        result = await client.search_games(query, limit, request_id)
        return result.model_dump(mode="json")

    @tool("steam_app_details", args_schema=SteamAppDetailsInput)
    async def steam_app_details(app_id: int) -> dict[str, object]:
        """Load verified name, description, and genres for one known Steam app id."""

        result = await client.get_steam_app_details(app_id, request_id)
        return result.model_dump(mode="json")

    return search_games, steam_app_details
