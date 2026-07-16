"""Explicit development-only GigaChat sample provider.

The real GigaChat integration was removed from the active roadmap.  Keeping
the sample data is useful for local UI work, but it must never look like a
successful production provider response.
"""

import logging
import os
from typing import List, Dict, Any

from app.observability import AI_METRICS, AIMetrics
from app.services.base import BaseAIService, RecommendationResult

logger = logging.getLogger(__name__)


class GigaChatService(BaseAIService):
    """Development-only sample provider guarded by an explicit flag."""

    MOCK_REPLY = "GigaChat provider is not implemented. Showing sample recommendations."

    def __init__(
        self,
        api_key: str | None = None,
        mock_enabled: bool | None = None,
        metrics: AIMetrics | None = None,
    ):
        super().__init__(api_key or os.getenv('GIGACHAT_API_KEY'))
        self.metrics = metrics or AI_METRICS
        self.mock_enabled = (
            mock_enabled
            if mock_enabled is not None
            else os.getenv("GIGACHAT_MOCK_ENABLED", "false").lower()
            in {"1", "true", "yes"}
        )
        if self.mock_enabled:
            self.name = "GigaChatMockService"
        self.base_url = "https://gigachat.devices.sberbank.ru/api/v1"

    async def get_recommendations(
        self,
        preferences: str,
        genres: List[str] | None = None,
        platforms: List[str] | None = None,
        max_recommendations: int = 5
    ) -> List[Dict[str, Any]]:
        """Return sample data only when the explicit development flag is set."""
        if not self.mock_enabled:
            raise RuntimeError("GigaChat provider is not implemented")

        logger.warning(
            "GigaChat development mock is active; returning visibly marked sample data"
        )
        self.metrics.record_ai_request("gigachat", "development-mock", "mock_fallback")
        self.metrics.record_mock_fallback("gigachat")
        recommendations = [
            {
                "title": "Red Dead Redemption 2",
                "genre": "Action-Adventure",
                "description": "Western action-adventure game set in 1899",
                "why_recommended": "Sample recommendation from the GigaChat development mock",
                "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X"],
                "rating": 9.7,
                "release_year": "2018",
            },
            {
                "title": "God of War (2018)",
                "genre": "Action-Adventure",
                "description": "Epic action-adventure with Norse mythology",
                "why_recommended": "Sample recommendation from the GigaChat development mock",
                "platforms": ["PC", "PS4", "PS5"],
                "rating": 9.4,
                "release_year": "2018",
            },
        ]
        return recommendations[:max_recommendations]

    async def get_recommendations_with_steam_library(
        self,
        user_message: str,
        selected_tags: List[str],
        steam_library: str | None,
        max_recommendations: int = 5,
        history: List[Dict[str, str]] | None = None,
        request_id: str | None = None,
    ) -> RecommendationResult:
        recommendations = await self.get_recommendations(
            user_message,
            genres=selected_tags,
            max_recommendations=max_recommendations,
        )
        return RecommendationResult(
            recommendations=recommendations,
            reply=self.MOCK_REPLY,
        )

    async def is_available(self) -> bool:
        """A sample provider is available only in explicit mock mode."""
        return self.mock_enabled
