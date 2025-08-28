FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy Poetry project files for dependency installation (build context is repo root)
COPY services/ai-service/pyproject.toml services/ai-service/poetry.lock ./

# Install dependencies using Poetry (system site-packages, no venv)
RUN pip install --no-cache-dir poetry && \
    poetry config virtualenvs.create false && \
    poetry install --no-interaction --no-ansi --only main --no-root

# Copy application code
COPY services/ai-service/app/ ./app/

# Create proto directory inside app and at root for generation
RUN mkdir -p app proto

# Copy proto files (will be generated during build)
COPY contracts/proto/reco.proto ./proto/

# Generate gRPC code into app package so it can be imported as app.reco_pb2
RUN python -m grpc_tools.protoc \
    -I./proto \
    --python_out=. \
    --grpc_python_out=. \
    ./proto/reco.proto

# Set environment variables so generated modules are importable
ENV PYTHONPATH=/app
ENV GRPC_PORT=9090
ENV HTTP_PORT=8000

# Expose ports
EXPOSE 8000 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/healthz || exit 1

# Run the application
CMD ["python", "-m", "app.main"]
