.PHONY: help proto.gen.py proto.gen.java up down clean logs test

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

proto.gen.py: ## Generate Python gRPC code from proto files
	docker run --rm -v $(PWD)/..:/repo -w /repo/services/ai-service python:3.11-slim \
	 bash -lc "pip install grpcio-tools && python -m grpc_tools.protoc -I./../../contracts/proto --python_out=./app --grpc_python_out=./app ./../../contracts/proto/reco.proto"

proto.gen.java: ## Generate Java gRPC code from proto files
	cd ../services/backend && mvn clean compile

proto.gen: proto.gen.py proto.gen.java ## Generate gRPC code for all languages

up: ## Start all services with docker-compose
	docker compose up --build -d

down: ## Stop all services
	docker compose down -v

clean: ## Clean up containers, images, and volumes
	docker compose down -v --rmi all --volumes --remove-orphans
	docker system prune -f

logs: ## Show logs from all services
	docker compose logs -f

logs.backend: ## Show backend logs
	docker compose logs -f backend

logs.ai: ## Show AI service logs
	docker compose logs -f ai-service

test: ## Test the services
	@echo "Testing AI service health..."
	@curl -s http://localhost:8000/healthz | jq .
	@echo "Testing backend health..."
	@curl -s http://localhost:8080/actuator/health | jq .
	@echo "Testing gRPC connection..."
	@curl -s http://localhost:8080/api/games/test

status: ## Show status of all services
	docker compose ps

restart: down up ## Restart all services

rebuild: ## Rebuild and restart all services
	docker compose down
	docker compose build --no-cache
	docker compose up -d
