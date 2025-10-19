.PHONY: help proto.gen.py proto.gen.py.local proto.gen.java proto.gen up down clean logs logs.backend logs.ai test status restart rebuild

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_.-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

proto.gen.py: ## Generate Python gRPC code from proto files (via Docker)
	docker run --rm -v $(PWD)/..:/repo -w /repo/services/ai-service python:3.11-slim \
	 bash -lc "pip install grpcio-tools && python -m grpc_tools.protoc -I./../../contracts/proto --python_out=./proto --grpc_python_out=./proto ./../../contracts/proto/reco.proto"

proto.gen.py.local: ## Generate Python gRPC code locally (requires grpcio-tools)
	cd ../services/ai-service && \
	python -m grpc_tools.protoc \
		-I../../contracts/proto \
		--python_out=./proto \
		--grpc_python_out=./proto \
		../../contracts/proto/reco.proto

proto.gen.java: ## Generate Java gRPC code from proto files
	cd ../services/backend && mvn clean compile

proto.gen: proto.gen.py proto.gen.java ## Generate gRPC code for all languages

up: ## Start all services with docker-compose
	docker compose up --build -d

down: ## Stop all services
	docker compose down

clean: ## Clean up containers, images, and volumes
	docker compose down -v --rmi all --volumes --remove-orphans
	docker system prune -f

logs: ## Show logs from all services
	docker compose logs -f

logs.backend: ## Show backend logs only
	docker compose logs -f backend

logs.ai: ## Show AI service logs only
	docker compose logs -f ai-service

test: ## Test the services
	@echo "Testing AI service health..."
	@curl -s http://localhost:8000/healthz | jq . || echo "AI service is not responding"
	@echo ""
	@echo "Testing backend health..."
	@curl -s http://localhost:8080/actuator/health | jq . || echo "Backend is not responding"
	@echo ""
	@echo "Testing authentication..."
	@curl -s -X POST http://localhost:8080/api/v1/auth/preAuthorize | jq . || echo "Auth endpoint is not responding"

status: ## Show status of all services
	docker compose ps

restart: down up ## Restart all services

rebuild: ## Rebuild and restart all services
	docker compose down
	docker compose build --no-cache
	docker compose up -d
