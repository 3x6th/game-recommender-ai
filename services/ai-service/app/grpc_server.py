"""
gRPC server implementation for the Game Recommender Service.
"""

import logging

from grpc import ServicerContext
from app.observability import bind_request_id, resolve_request_id
from app.services.registry import ServiceRegistry
import sys
from pathlib import Path

# Add proto directory to Python path
sys.path.insert(0, str(Path(__file__).parent.parent / "proto"))
import reco_pb2  # type: ignore[import-not-found]  # noqa: E402
import reco_pb2_grpc  # type: ignore[import-not-found]  # noqa: E402

logger = logging.getLogger(__name__)

PUBLIC_AI_ERROR = "AI recommendation is temporarily unavailable"


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
            metadata = context.invocation_metadata()
        except Exception:
            metadata = None
        request_id = resolve_request_id(
            metadata,
            request.requestId,
            request.correlationId,
        )
        with bind_request_id(request_id):
            return await self._recommend(request, context, request_id)

    async def _recommend(
        self,
        request: reco_pb2.FullAiContextRequestProto,
        context: ServicerContext,
        request_id: str,
    ) -> reco_pb2.RecommendationResponse:
        try:
            logger.info(
                "Received full context recommendation request request_id=%s",
                request_id,
            )
            logger.info(
                "Recommendation request received, message_chars=%d selected_tags=%d",
                len(request.userMessage),
                len(request.selectedTags),
            )

            history = []
            for message in request.history:
                if message.role == reco_pb2.ChatHistoryMessageProto.ROLE_USER:
                    role = "user"
                elif message.role == reco_pb2.ChatHistoryMessageProto.ROLE_ASSISTANT:
                    role = "assistant"
                else:
                    continue
                if message.text:
                    history.append({"role": role, "content": message.text})
            logger.info("Received chat history: message_count=%d", len(history))

            # Get recommendations, reasoning from service registry with Steam library context
            result = await self.service_registry.get_recommendations_with_steam_library(
                user_message=request.userMessage,
                selected_tags=list(request.selectedTags),
                steam_library=request.profileSummary,
                max_recommendations=request.maxResults,
                history=history,
                request_id=request_id,
            )

            # Convert to gRPC format
            grpc_recommendations = []
            for rec in result.recommendations:
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
                message=result.reply,
                reasoning=result.reasoning,
                recommendations=grpc_recommendations,
                provider=self.service_registry.get_active_provider()
            )

        except Exception as e:
            logger.error(
                "RecommendGames failed request_id=%s error_type=%s",
                request_id,
                type(e).__name__,
            )
            return reco_pb2.RecommendationResponse(
                success=False,
                message=PUBLIC_AI_ERROR,
                reasoning="",
                recommendations=[],
                provider=""
            )
