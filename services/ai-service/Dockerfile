FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/

# Create proto directory
RUN mkdir -p proto

# Copy proto files (will be generated during build)
COPY ../../contracts/proto/reco.proto ./proto/

# Generate gRPC code
RUN python -m grpc_tools.protoc \
    -I./proto \
    --python_out=./app \
    --grpc_python_out=./app \
    ./proto/reco.proto

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
