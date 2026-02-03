"""
gRPC server implementation for the Game Recommender Service.
"""

import asyncio
import logging
from typing import List

import grpc
from grpc import ServicerContext
from app.services.registry import ServiceRegistry
import sys
from pathlib import Path

# Add proto directory to Python path
sys.path.insert(0, str(Path(__file__).parent.parent / "proto"))
import reco_pb2
import reco_pb2_grpc

logger = logging.getLogger(__name__)

class GameRecommenderServicer(reco_pb2_grpc.GameRecommenderServiceServicer):
    """gRPC servicer for game recommendations"""
    
    def __init__(self, service_registry: ServiceRegistry):
        self.service_registry = service_registry

    async def RecommendGames(
            self,
            request: reco_pb2.FullAiContextRequestProto,
            context: ServicerContext
    ) -> reco_pb2.RecommendationResponse:
        """Handle game recommendations with full context including Steam library"""
        try:
            logger.info("Received full context recommendation request")
            logger.info(f"User message: {request.userMessage}")
            logger.info(f"Selected tags: {request.selectedTags}")

            # Get recommendations from service registry with Steam library context
            recommendations = await self.service_registry.get_recommendations_with_steam_library(
                user_message=request.userMessage,
                selected_tags=list(request.selectedTags),
                steam_library=request.userSteamLibrary,
                max_recommendations=5
            )

            # Convert to gRPC format
            grpc_recommendations = []
            for rec in recommendations:
                grpc_rec = reco_pb2.GameRecommendation(
                    title=rec.get('title', ''),
                    genre=rec.get('genre', ''),
                    description=rec.get('description', ''),
                    why_recommended=rec.get('why_recommended', ''),
                    platforms=rec.get('platforms', []),
                    rating=rec.get('rating', 0.0),
                    release_year=rec.get('release_year', '')
                )
                grpc_recommendations.append(grpc_rec)

            return reco_pb2.RecommendationResponse(
                success=True,
                message=f"Generated {len(grpc_recommendations)} recommendations based on preferences and Steam library",
                recommendations=grpc_recommendations,
                provider=self.service_registry.get_active_provider()
            )

        except Exception as e:
            logger.error(f"Error in RecommendGames: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return reco_pb2.RecommendationResponse(
                success=False,
                message=f"Error: {str(e)}",
                recommendations=[],
                provider=""
            )
        
