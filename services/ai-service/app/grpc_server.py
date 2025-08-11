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
        
    async def Recommend(self, request: reco_pb2.RecommendationRequest, context: ServicerContext) -> reco_pb2.RecommendationResponse:
        """Handle recommendation requests"""
        try:
            logger.info(f"Received recommendation request: {request.preferences}")
            
            # Get recommendations from service registry
            recommendations = await self.service_registry.get_recommendations(
                preferences=request.preferences,
                genres=list(request.genres),
                platforms=list(request.platforms),
                max_recommendations=request.max_recommendations or 5
            )
            
            logger.info(f"Received {len(recommendations)} recommendations from service registry")
            if recommendations:
                logger.info(f"First recommendation: {recommendations[0]}")
            
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
            
            logger.info(f"Converted {len(grpc_recommendations)} recommendations to gRPC format")
            
            return reco_pb2.RecommendationResponse(
                success=True,
                message=f"Generated {len(grpc_recommendations)} recommendations",
                recommendations=grpc_recommendations,
                provider=self.service_registry.get_active_provider()
            )
            
        except Exception as e:
            logger.error(f"Error in Recommend: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return reco_pb2.RecommendationResponse(
                success=False,
                message=f"Error: {str(e)}",
                recommendations=[],
                provider=""
            )
    
    async def Chat(self, request: reco_pb2.ChatRequest, context: ServicerContext) -> reco_pb2.ChatResponse:
        """Handle chat requests"""
        try:
            logger.info(f"Received chat request: {request.message}")
            
            # Get chat response from service registry
            response = await self.service_registry.chat(
                message=request.message,
                context=request.context or ""
            )
            
            return reco_pb2.ChatResponse(
                success=True,
                message="Chat response generated successfully",
                ai_response=response,
                provider=self.service_registry.get_active_provider()
            )
            
        except Exception as e:
            logger.error(f"Error in Chat: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return reco_pb2.ChatResponse(
                success=False,
                message=f"Error: {str(e)}",
                ai_response="",
                provider=""
            )
