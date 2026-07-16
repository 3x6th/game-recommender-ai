#!/usr/bin/env python3
"""
Main entry point for the AI Service.
Runs both gRPC server and FastAPI HTTP server.
"""

import logging
import os
import sys
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from dotenv import load_dotenv
from grpc import aio

from app.grpc_server import GameRecommenderServicer
from app.http_api import create_app
from app.observability import configure_request_context_logging
from app.services.registry import ServiceRegistry

# Load environment variables from .env file
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format=(
        '%(asctime)s - %(name)s - %(levelname)s '
        '[request_id=%(request_id)s] - %(message)s'
    )
)
configure_request_context_logging()
logger = logging.getLogger(__name__)

# Configuration
GRPC_PORT = int(os.getenv('GRPC_PORT', '9090'))
HTTP_PORT = int(os.getenv('HTTP_PORT', '8000'))
GRPC_HOST = os.getenv('GRPC_HOST', '[::]')


class AIService:
    def __init__(self) -> None:
        self.grpc_server: aio.Server | None = None
        self.service_registry = ServiceRegistry()

    async def start_grpc_server(self) -> None:
        """Start the gRPC server"""
        try:
            self.grpc_server = aio.server()

            # Add the servicer
            servicer = GameRecommenderServicer(self.service_registry)

            # Import gRPC generated code
            from pathlib import Path

            sys.path.insert(0, str(Path(__file__).parent.parent / "proto"))
            import reco_pb2_grpc  # type: ignore[import-not-found]

            reco_pb2_grpc.add_GameRecommenderServiceServicer_to_server(
                servicer,
                self.grpc_server,
            )

            # Bind to port
            listen_addr = f'{GRPC_HOST}:{GRPC_PORT}'
            self.grpc_server.add_insecure_port(listen_addr)

            await self.grpc_server.start()
            logger.info("gRPC server started on %s", listen_addr)

        except Exception:
            logger.exception("Failed to start gRPC server")
            raise

    async def stop(self) -> None:
        """Stop gRPC server"""
        logger.info("Stopping AI Service...")

        if self.grpc_server:
            await self.grpc_server.stop(grace=5)
            logger.info("gRPC server stopped")

        await self.service_registry.close()

        logger.info("AI Service stopped")


# Global service instance
ai_service: AIService | None = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Lifespan context manager for FastAPI"""
    global ai_service

    # Startup
    ai_service = AIService()
    await ai_service.start_grpc_server()
    app.state.ai_service = ai_service

    yield

    # Shutdown
    await ai_service.stop()


def create_app_with_lifespan() -> FastAPI:
    """Create FastAPI app with lifespan"""
    app = create_app()
    app.router.lifespan_context = lifespan
    return app


def main() -> None:
    """Main entry point - run FastAPI with uvicorn"""
    try:
        app = create_app_with_lifespan()

        # Start with uvicorn
        uvicorn.run(
            app,
            host="0.0.0.0",
            port=HTTP_PORT,
            log_level="info"
        )

    except KeyboardInterrupt:
        logger.info("Received interrupt signal, shutting down...")
    except Exception:
        logger.exception("Service failed")
        sys.exit(1)


if __name__ == "__main__":
    main()
