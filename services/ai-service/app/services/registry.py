"""
Service registry for managing AI service providers.
"""

import logging
import os
from typing import List, Dict, Any

from langchain_core.tools import BaseTool

from app.services.base import BaseAIService, RecommendationResult
from app.services.deepseek_service import DeepSeekService
from app.services.gigachat_service import GigaChatService
from app.tools import JavaToolsClient, create_java_tools

logger = logging.getLogger(__name__)

class ServiceRegistry:
    """Registry for AI service providers"""
    
    def __init__(self):
        self.services: List[BaseAIService] = []
        self.active_service: BaseAIService | None = None
        self.java_tools_client: JavaToolsClient | None = None
        self._initialize_services()
    
    def _initialize_services(self):
        """Initialize available AI services"""
        try:
            # Add DeepSeek service if API key is available
            mock_fallback_enabled = os.getenv(
                "AI_MOCK_FALLBACK_ENABLED", "false"
            ).lower() in {"1", "true", "yes"}
            if os.getenv('DEEPSEEK_API_KEY') or mock_fallback_enabled:
                self.java_tools_client = JavaToolsClient()
                deepseek_service = DeepSeekService(
                    agent_tool_factory=self._create_agent_tools,
                )
                self.services.append(deepseek_service)
                logger.info(
                    "DeepSeek service initialized, mock_fallback_enabled=%s",
                    mock_fallback_enabled,
                )
            
            # The real GigaChat adapter is not implemented.  An API key alone
            # must never activate the hardcoded development sample provider.
            gigachat_mock_enabled = os.getenv(
                "GIGACHAT_MOCK_ENABLED", "false"
            ).lower() in {"1", "true", "yes"}
            if gigachat_mock_enabled:
                gigachat_service = GigaChatService(mock_enabled=True)
                self.services.append(gigachat_service)
                logger.warning("GigaChat development mock initialized explicitly")
            
            # Set active service (first available one)
            if self.services:
                self.active_service = self.services[0]
                logger.info(f"Active service set to: {self.active_service.get_name()}")
            else:
                logger.warning("No AI services available")
                
        except Exception as e:
            logger.error(f"Error initializing services: {e}")

    def _create_agent_tools(
        self,
        request_id: str | None,
    ) -> tuple[BaseTool, ...]:
        client = self.java_tools_client
        return create_java_tools(client, request_id) if client is not None else ()
    
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
        genres: List[str] | None = None,
        platforms: List[str] | None = None,
        max_recommendations: int = 5
    ) -> List[Dict[str, Any]]:
        """Get recommendations from active service"""
        if not self.active_service:
            logger.error("No active AI service")
            raise RuntimeError("No active AI service")
        
        try:
            logger.info(f"Getting recommendations from {self.active_service.get_name()}")
            recommendations = await self.active_service.get_recommendations(
                preferences, genres, platforms, max_recommendations
            )
            logger.info(f"Service {self.active_service.get_name()} returned {len(recommendations)} recommendations")
            return recommendations
        except Exception as e:
            logger.error(f"Error getting recommendations: {e}")
            raise

    async def get_recommendations_with_steam_library(
            self,
            user_message: str,
            selected_tags: List[str],
            steam_library: str | None,
            max_recommendations: int = 5,
            history: List[Dict[str, str]] | None = None,
            request_id: str | None = None,
    ) -> RecommendationResult:
        """Get recommendations based on user preferences and Steam library"""
        if not self.active_service:
            logger.error("No active AI service")
            raise RuntimeError("No active AI service")

        try:
            logger.info(f"Getting recommendations from {self.active_service.get_name()} with Steam library data")
            result = await self.active_service.get_recommendations_with_steam_library(
                user_message,
                selected_tags,
                steam_library,
                max_recommendations,
                history,
                request_id,
            )
            logger.info(
                "Service %s returned %d recommendations, has_reply=%s",
                self.active_service.get_name(),
                len(result.recommendations),
                bool(result.reply),
            )
            if result.reasoning:
                logger.info(f"Reasoning: {result.reasoning}")
            return result
        except Exception as e:
            logger.error(f"Error getting recommendations with Steam library: {e}")
            raise

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

    async def close(self) -> None:
        """Release lazy outbound resources created by active providers."""

        if self.java_tools_client is not None:
            await self.java_tools_client.close()
