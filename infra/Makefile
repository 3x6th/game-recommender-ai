.PHONY: help up down build logs status clean test

help: ## Показать справку
	@echo "Доступные команды:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: ## Запустить все сервисы
	docker-compose up -d

down: ## Остановить все сервисы
	docker-compose down

build: ## Пересобрать и запустить сервисы
	docker-compose down
	docker-compose build --no-cache
	docker-compose up -d

logs: ## Показать логи всех сервисов
	docker-compose logs -f

logs-backend: ## Показать логи backend сервиса
	docker-compose logs -f backend

logs-ai: ## Показать логи AI сервиса
	docker-compose logs -f ai-service

logs-redis: ## Показать логи Redis
	docker-compose logs -f redis

status: ## Показать статус сервисов
	docker-compose ps

clean: ## Очистить все контейнеры и volumes
	docker-compose down -v
	docker system prune -f

test: ## Запустить тесты
	@echo "Тестирование backend сервиса..."
	cd ../services/backend && ./mvnw test
	@echo "Тестирование AI сервиса..."
	cd ../services/ai-service && python -m pytest

health: ## Проверить здоровье сервисов
	@echo "Проверка backend..."
	curl -f http://localhost:8080/actuator/health || echo "Backend недоступен"
	@echo "Проверка Redis..."
	docker-compose exec redis redis-cli ping || echo "Redis недоступен"

monitor: ## Мониторинг ресурсов
	@echo "Использование памяти:"
	docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"
	@echo ""
	@echo "Статус сервисов:"
	docker-compose ps

redis-cli: ## Подключиться к Redis CLI
	docker-compose exec redis redis-cli

restart: ## Перезапустить все сервисы
	docker-compose restart

restart-backend: ## Перезапустить только backend
	docker-compose restart backend

restart-ai: ## Перезапустить только AI сервис
	docker-compose restart ai-service
