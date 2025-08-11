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
    
    @abstractmethod
    async def chat(self, message: str, context: str = "") -> str:
        """Chat with AI"""
        pass
    
    @abstractmethod
    async def is_available(self) -> bool:
        """Check if service is available"""
        pass
    
    def get_name(self) -> str:
        """Get service name"""
        return self.name
