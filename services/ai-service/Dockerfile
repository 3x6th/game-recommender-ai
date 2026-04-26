FROM python:3.11-slim

WORKDIR /app

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    POETRY_VERSION=2.1.3 \
    POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_CREATE=false \
    PYTHON_KEYRING_BACKEND=keyring.backends.null.Keyring

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry
RUN pip install "poetry==${POETRY_VERSION}"

# Copy Poetry files
COPY services/ai-service/pyproject.toml services/ai-service/poetry.lock ./

RUN poetry install --no-root

# Create proto directory and copy proto files
RUN mkdir -p proto
COPY contracts/proto/reco.proto ./proto/

# Generate gRPC code до копирования app кода
RUN python -m grpc_tools.protoc \
    -I./proto \
    --python_out=./proto \
    --grpc_python_out=./proto \
    ./proto/reco.proto

# Copy application code
COPY services/ai-service/app/ ./app/

# Set environment variables
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
