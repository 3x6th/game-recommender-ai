"""
Service registry for managing AI service providers.
"""

import logging
import os
from typing import List, Dict, Any, Tuple

from app.services.base import BaseAIService
from app.services.deepseek_service import DeepSeekService
from app.services.gigachat_service import GigaChatService

logger = logging.getLogger(__name__)

class ServiceRegistry:
    """Registry for AI service providers"""
    
    def __init__(self):
        self.services: List[BaseAIService] = []
        self.active_service: BaseAIService = None
        self._initialize_services()
    
    def _initialize_services(self):
        """Initialize available AI services"""
        try:
            # Add DeepSeek service if API key is available
            if os.getenv('DEEPSEEK_API_KEY'):
                deepseek_service = DeepSeekService()
                self.services.append(deepseek_service)
                logger.info("DeepSeek service initialized")
            
            # Add GigaChat service if API key is available
            if os.getenv('GIGACHAT_API_KEY'):
                gigachat_service = GigaChatService()
                self.services.append(gigachat_service)
                logger.info("GigaChat service initialized")
            
            # Set active service (first available one)
            if self.services:
                self.active_service = self.services[0]
                logger.info(f"Active service set to: {self.active_service.get_name()}")
            else:
                logger.warning("No AI services available")
                
        except Exception as e:
            logger.error(f"Error initializing services: {e}")
    
    def get_active_provider(self) -> str:
        """Get name of active provider"""
        return self.active_service.get_name() if self.active_service else "none"
    
    def switch_service(self, service_name: str) -> bool:
        """Switch to a different service"""
        for service in self.services:
            if service.get_name().lower() == service_name.lower():
                self.active_service = service
                logger.info(f"Switched to service: {service.get_name()}")
                return True
        logger.warning(f"Service not found: {service_name}")
        return False
    
    async def get_recommendations(
        self, 
        preferences: str, 
        genres: List[str] = None, 
        platforms: List[str] = None,
        max_recommendations: int = 5
    ) -> List[Dict[str, Any]]:
        """Get recommendations from active service"""
        if not self.active_service:
            logger.error("No active AI service")
            return []
        
        try:
            logger.info(f"Getting recommendations from {self.active_service.get_name()}")
            recommendations = await self.active_service.get_recommendations(
                preferences, genres, platforms, max_recommendations
            )
            logger.info(f"Service {self.active_service.get_name()} returned {len(recommendations)} recommendations")
            return recommendations
        except Exception as e:
            logger.error(f"Error getting recommendations: {e}")
            return []

    async def get_recommendations_with_steam_library(
            self,
            user_message: str,
            selected_tags: List[str],
            steam_library: Dict[str, Any],
            max_recommendations: int = 5
    ) -> Tuple[List[Dict[str, Any]], str]:
        """Get recommendations based on user preferences and Steam library"""
        if not self.active_service:
            logger.error("No active AI service")
            return [], ""

        try:
            logger.info(f"Getting recommendations from {self.active_service.get_name()} with Steam library data")
            recommendations, reasoning = await self.active_service.get_recommendations_with_steam_library(
                user_message, selected_tags, steam_library, max_recommendations
            )
            logger.info(f"Service {self.active_service.get_name()} returned {len(recommendations)} recommendations")
            if reasoning:
                logger.info(f"Reasoning: {reasoning}")
            return recommendations, reasoning
        except Exception as e:
            logger.error(f"Error getting recommendations with Steam library: {e}")
            return [], ""

    def get_available_services(self) -> List[str]:
        """Get list of available service names"""
        return [service.get_name() for service in self.services]
    
    async def check_service_health(self) -> Dict[str, bool]:
        """Check health of all services"""
        health_status = {}
        for service in self.services:
            try:
                health_status[service.get_name()] = await service.is_available()
            except Exception as e:
                logger.error(f"Error checking health of {service.get_name()}: {e}")
                health_status[service.get_name()] = False
        return health_status
