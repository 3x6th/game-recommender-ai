"""
Base class for AI service providers.
"""

from abc import ABC, abstractmethod
from typing import List, Dict, Any

class BaseAIService(ABC):
    """Base class for AI service providers"""
    
    def __init__(self, api_key: str = None):
        self.api_key = api_key
        self.name = self.__class__.__name__
    
    @abstractmethod
    async def get_recommendations(
        self, 
        preferences: str, 
        genres: List[str] = None, 
        platforms: List[str] = None,
        max_recommendations: int = 5
    ) -> List[Dict[str, Any]]:
        """Get game recommendations"""
        pass

    async def get_recommendations_with_steam_library(
        self,
        user_message: str,
        selected_tags: List[str],
        steam_library: str | None,
        max_recommendations: int = 5,
        history: List[Dict[str, str]] | None = None,
    ) -> tuple[List[Dict[str, Any]], str]:
        """Provider-neutral fallback for services without profile-aware prompts."""
        recommendations = await self.get_recommendations(
            user_message,
            genres=selected_tags,
            max_recommendations=max_recommendations,
        )
        return recommendations, ""
    
    @abstractmethod
    async def is_available(self) -> bool:
        """Check if service is available"""
        pass
    
    def get_name(self) -> str:
        """Get service name"""
        return self.name
