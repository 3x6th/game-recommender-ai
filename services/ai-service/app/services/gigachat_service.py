"""
GigaChat AI service implementation.
"""

import logging
import os
from typing import List, Dict, Any

from app.services.base import BaseAIService

logger = logging.getLogger(__name__)

class GigaChatService(BaseAIService):
    """GigaChat AI service provider"""
    
    def __init__(self, api_key: str = None):
        super().__init__(api_key or os.getenv('GIGACHAT_API_KEY'))
        self.base_url = "https://gigachat.devices.sberbank.ru/api/v1"
        
    async def get_recommendations(
        self, 
        preferences: str, 
        genres: List[str] = None, 
        platforms: List[str] = None,
        max_recommendations: int = 5
    ) -> List[Dict[str, Any]]:
        """Get game recommendations from GigaChat"""
        try:
            # TODO: Implement actual GigaChat API call
            logger.info(f"Getting recommendations from GigaChat: {preferences}")
            
            # Mock response
            recommendations = [
                {
                    "title": "Red Dead Redemption 2",
                    "genre": "Action-Adventure",
                    "description": "Western action-adventure game set in 1899",
                    "why_recommended": "Perfect for action game lovers with immersive storytelling",
                    "platforms": ["PC", "PS4", "PS5", "Xbox One", "Xbox Series X"],
                    "rating": 9.7,
                    "release_year": "2018"
                },
                {
                    "title": "God of War (2018)",
                    "genre": "Action-Adventure",
                    "description": "Epic action-adventure with Norse mythology",
                    "why_recommended": "Intense action combat with compelling narrative",
                    "platforms": ["PC", "PS4", "PS5"],
                    "rating": 9.4,
                    "release_year": "2018"
                }
            ]
            
            return recommendations[:max_recommendations]
            
        except Exception as e:
            logger.error(f"Error getting recommendations from GigaChat: {e}")
            return []
    
    async def chat(self, message: str, context: str = "") -> str:
        """Chat with GigaChat AI"""
        try:
            # TODO: Implement actual GigaChat chat API call
            logger.info(f"Chatting with GigaChat: {message}")
            
            # Mock response
            return f"GigaChat AI: I understand you're asking about '{message}'. This is a mock response - implement actual API integration here."
            
        except Exception as e:
            logger.error(f"Error chatting with GigaChat: {e}")
            return f"Sorry, I encountered an error: {str(e)}"
    
    async def is_available(self) -> bool:
        """Check if GigaChat service is available"""
        return bool(self.api_key)
