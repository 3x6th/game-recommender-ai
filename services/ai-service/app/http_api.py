"""
FastAPI HTTP server for health checks and metrics.
"""

import logging
from datetime import datetime
from typing import Dict, Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response
from prometheus_client import CONTENT_TYPE_LATEST, CollectorRegistry, generate_latest

from app.observability import AI_METRICS

logger = logging.getLogger(__name__)


def create_app(
    metrics_registry: CollectorRegistry | None = None,
) -> FastAPI:
    """Create and configure FastAPI application"""

    app = FastAPI(
        title="AI Service",
        description="AI Service for Game Recommendations",
        version="1.0.0",
        docs_url=None,  # Disable Swagger UI
        redoc_url=None   # Disable ReDoc
    )
    registry = metrics_registry or AI_METRICS.registry

    @app.get("/healthz")
    async def health_check() -> Dict[str, Any]:
        """Health check endpoint"""
        return {
            "status": "ok",
            "timestamp": datetime.utcnow().isoformat(),
            "service": "ai-service",
            "version": "1.0.0"
        }

    @app.get("/status")
    async def service_status(request: Request) -> Dict[str, Any]:
        """Get detailed service status including AI providers"""
        try:
            # Get AI service from app state
            ai_service = getattr(request.app.state, 'ai_service', None)
            if ai_service and hasattr(ai_service, 'service_registry'):
                registry = ai_service.service_registry
                services_status = {}

                for service in registry.services:
                    service_name = service.get_name()
                    if hasattr(service, 'get_circuit_breaker_status'):
                        services_status[service_name] = {
                            "available": await service.is_available(),
                            "circuit_breaker": service.get_circuit_breaker_status()
                        }
                    else:
                        services_status[service_name] = {
                            "available": await service.is_available()
                        }

                return {
                    "status": "ok",
                    "timestamp": datetime.utcnow().isoformat(),
                    "active_provider": registry.get_active_provider(),
                    "available_services": registry.get_available_services(),
                    "services_status": services_status
                }
            else:
                return {
                    "status": "error",
                    "message": "AI service not available",
                    "timestamp": datetime.utcnow().isoformat()
                }

        except Exception as e:
            logger.error(
                "Error getting service status, error_type=%s",
                type(e).__name__,
            )
            return {
                "status": "error",
                "message": "Service status is temporarily unavailable",
                "timestamp": datetime.utcnow().isoformat()
            }

    @app.get("/metrics", include_in_schema=False)
    async def metrics() -> Response:
        """Expose the configured Prometheus collector registry."""
        return Response(
            content=generate_latest(registry),
            media_type=CONTENT_TYPE_LATEST,
        )

    @app.get("/")
    async def root() -> Dict[str, Any]:
        """Root endpoint"""
        return {
            "message": "AI Service is running",
            "endpoints": {
                "health": "/healthz",
                "status": "/status",
                "metrics": "/metrics"
            }
        }

    @app.exception_handler(Exception)
    async def global_exception_handler(
        request: Request,
        exc: Exception,
    ) -> JSONResponse:
        """Global exception handler"""
        logger.error(
            "Unhandled HTTP exception, error_type=%s",
            type(exc).__name__,
        )
        return JSONResponse(
            status_code=500,
            content={
                "error": "Internal server error",
                "message": "The request could not be completed",
                "timestamp": datetime.utcnow().isoformat()
            }
        )

    return app
