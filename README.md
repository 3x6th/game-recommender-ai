# PlayCure - Game Recommendation AI Platform

Монорепозиторий с Java Spring Boot backend, Python AI сервисом и PostgreSQL базой данных для сервиса подбора игр при игровом выгорании.

## 🏗️ Архитектура

```
game-recommender-ai/
├── contracts/           # Общие контракты
│   └── proto/          # gRPC proto файлы
├── services/            # Микросервисы
│   ├── backend/        # Java 21 Spring Boot (порт 8080)
│   │   ├── controller/ # REST API контроллеры (Auth, Games, Steam)
│   │   ├── service/    # Бизнес-логика
│   │   ├── repository/ # JPA репозитории
│   │   ├── entity/     # JPA сущности
│   │   ├── security/   # JWT аутентификация и авторизация
│   │   ├── config/     # Конфигурация (CORS, gRPC, Security, Cache)
│   │   └── client/     # gRPC и HTTP клиенты
│   └── ai-service/     # Python 3.11 gRPC + FastAPI (порты 8000, 9090)
│       ├── grpc_server.py  # gRPC сервер для рекомендаций
│       ├── http_api.py     # FastAPI для health/metrics
│       └── services/       # AI провайдеры (DeepSeek, GigaChat)
├── infra/              # Инфраструктура и оркестрация
│   ├── docker-compose.yml          # Deployment-safe compose (наружу опубликован только backend)
│   ├── docker-compose.dev.yml      # Local dev override (порты внутренних сервисов только на localhost)
│   ├── observability-compose.yml   # Prometheus + Grafana
│   └── grafana/                    # Дашборды мониторинга
└── logs/               # Логи приложения
```

## 🚀 Быстрый старт

### Предварительные требования

- **Docker** и **Docker Compose** v2.0+
- **Java 21** (для локальной разработки backend)
- **Maven 3.8+** (для локальной разработки backend)
- **Python 3.11** (для локальной разработки AI service)
- **Poetry** (для локальной разработки AI service)
- **PostgreSQL 17** (автоматически через Docker)
- **Redis 7** (автоматически через Docker)

### 1. Клонирование и настройка

```bash
git clone <repository-url>
cd game-recommender-ai

# Скопировать переменные окружения для основного проекта
cp env.example .env

# Настроить переменные окружения для инфраструктуры
cd infra
cp ../env.example .env

# Отредактировать .env файл с вашими настройками:
# - DEEPSEEK_API_KEY - ключ для DeepSeek AI
# - POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB - настройки БД
# - JWT_SECRET, JWT_REFRESH_SECRET - секреты для JWT токенов
nano .env
```

### 2. Запуск с Docker Compose

В проекте используется разделение compose файлов:

- `infra/docker-compose.yml` — **deployment-safe**: Redis/PostgreSQL/AI сервис **не публикуют порты на хост**, доступны только внутри Docker сети.
- `infra/docker-compose.dev.yml` — **только для локальной разработки**: добавляет порты внутренних сервисов, привязанные к `127.0.0.1`.

#### Локальная разработка

```bash
cd infra
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

#### Deployment / VPS / Dokploy

```bash
cd infra
docker compose -f docker-compose.yml up --build -d
```

> Важно: **не подключайте** `docker-compose.dev.yml` на сервере — он публикует внутренние порты (пусть и на `127.0.0.1`) и нужен только для удобства разработки.

### 3. Тестирование

```bash
# Локально (с docker-compose.dev.yml)
curl http://localhost:8000/healthz          # AI Service health
curl http://localhost:8080/actuator/health  # Backend health
curl -X POST http://localhost:8080/api/v1/auth/preAuthorize  # Создать анонимную сессию

# Проверить базу данных
docker exec -it game-recommender-postgres-db psql -U postgres -d game_recommender_ai -c "SELECT version();"
```

### 4. Доступ к сервисам

После запуска будут доступны:

**Локальная разработка** (c `docker-compose.dev.yml`):
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs
- **AI Service Health**: http://localhost:8000/healthz
- **AI Service Metrics**: http://localhost:8000/metrics
- **AI Service gRPC**: localhost:9090
- **PostgreSQL**: localhost:5433
- **Redis**: localhost:6379
- **Prometheus**: http://localhost:9091 (если запущен observability stack)
- **Grafana**: http://localhost:3000 (если запущен observability stack)

**Deployment** (только `docker-compose.yml`):
- **Опубликован только Backend** (порт 8080)
- **Redis/PostgreSQL/AI Service** доступны **только внутри Docker сети** по именам сервисов: `redis:6379`, `game_recommender_ai_db:5432`, `ai-service:9090`

## 🔧 Разработка

### Генерация gRPC кода (опционально)

Обычно gRPC код генерируется автоматически при сборке Docker образов.

Если нужно пересобрать локально:

- **Java**: `cd services/backend && mvn clean compile`
- **Python**: `cd services/ai-service && poetry run python -m grpc_tools.protoc -I../../contracts/proto --python_out=./proto --grpc_python_out=./proto ../../contracts/proto/reco.proto`

### Локальная разработка

#### Backend (Java)

**Предварительная настройка:**
1. Убедитесь, что PostgreSQL и Redis запущены (можно через Docker Compose)
2. Настройте `application.yml` или переменные окружения

```bash
cd services/backend

# Сборка проекта и генерация gRPC кода
mvn clean compile

# Запуск с профилем dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Или напрямую через JAR
mvn clean package
java -jar target/game-recommender-ai-backend-1.0.0-SNAPSHOT.jar
```

**Полезные команды:**
```bash
# Запустить тесты
mvn test

# Проверить код стиль (Checkstyle)
mvn checkstyle:check

# Проверить покрытие тестами (JaCoCo)
mvn jacoco:report

# Применить миграции БД (Liquibase)
mvn liquibase:update
```

#### AI Service (Python)

```bash
cd services/ai-service

# Установить зависимости
poetry install

# Сгенерировать gRPC код
poetry run python -m grpc_tools.protoc -I../../contracts/proto \
  --python_out=./proto \
  --grpc_python_out=./proto \
  ../../contracts/proto/reco.proto

# Запустить сервис
poetry run python -m app.main

# Или через uvicorn (для разработки с hot reload)
poetry run uvicorn app.http_api:app --reload --port 8000
```

### Полезные команды

```bash
# Локальная разработка
cd infra
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f
docker compose -f docker-compose.yml -f docker-compose.dev.yml down

# Deployment / VPS / Dokploy (НЕ добавляйте docker-compose.dev.yml)
cd infra
docker compose -f docker-compose.yml up --build -d
docker compose -f docker-compose.yml ps
docker compose -f docker-compose.yml logs -f
docker compose -f docker-compose.yml down
```

## 📡 API Endpoints

### Backend REST API (Java Spring Boot)

#### Аутентификация (`/api/v1/auth`)
- **POST /api/v1/auth/preAuthorize** - Создание анонимной сессии
  - Response: `{ accessToken, refreshToken (в cookie), sessionId, role }`
- **POST /api/v1/auth/refresh** - Обновление access token через refresh token
- **GET /api/v1/auth/steam** - Редирект на Steam OAuth
- **GET /api/v1/auth/steam/return** - Callback от Steam после авторизации

#### Игры и рекомендации (`/api/games`)
- **POST /api/games/proceed** - Получить рекомендации игр с учетом Steam библиотеки
  - Body: `{ content: string, tags: string[], steamId?: string }`
  - Доступно гостям и авторизованным пользователям

#### Мониторинг и метрики
- **GET /actuator/health** - Статус здоровья приложения
- **GET /actuator/metrics** - Метрики приложения
- **GET /actuator/prometheus** - Метрики для Prometheus
- **GET /swagger-ui.html** - Swagger UI документация
- **GET /api-docs** - OpenAPI спецификация

### AI Service (Python)
- **GET /healthz** - Health check
- **GET /metrics** - Basic metrics
- **gRPC** - `localhost:9090` - gRPC сервис для рекомендаций

## 🔌 gRPC API

### GameRecommenderService

```protobuf
service GameRecommenderService {
  rpc RecommendGames(FullAiContextRequestProto) returns (RecommendationResponse);
}
```

### Примеры использования

#### Java Client
```java
@Autowired
private GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub stub;

FullAiContextRequestProto request = FullAiContextRequestProto.newBuilder()
    .setUserMessage("action games with good story")
    .build();

RecommendationResponse response = stub.recommendGames(request);
```

#### Python Client
```python
import grpc
from app.proto import reco_pb2, reco_pb2_grpc

channel = grpc.insecure_channel('localhost:9090')
stub = reco_pb2_grpc.GameRecommenderServiceStub(channel)

request = reco_pb2.FullAiContextRequestProto(
    userMessage="action games with good story"
)

response = stub.RecommendGames(request)
```

## 🤖 AI Providers

### Поддерживаемые провайдеры

- **DeepSeek** - основной AI провайдер для генерации рекомендаций
  - Модель: `deepseek-chat`
  - Используется для анализа предпочтений и подбора игр
- **GigaChat** - альтернативный провайдер (опционально)
  - Может использоваться как fallback

### Конфигурация

```bash
# В .env файле (корень проекта и infra/)
DEEPSEEK_API_KEY=your-deepseek-api-key
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_MAX_TOKENS=1000
DEEPSEEK_TEMPERATURE=0.7

# Опционально
GIGACHAT_API_KEY=your-gigachat-api-key
```

## 🐳 Docker

### Образы и сервисы

- **backend**: Java 21 + Spring Boot + Maven
  - Использует multi-stage build для оптимизации размера
  - Включает gRPC клиент для связи с AI Service
- **ai-service**: Python 3.11 + gRPC + FastAPI
  - Запускает два сервера: gRPC (9090) и HTTP (8000)
- **game_recommender_ai_db**: PostgreSQL 17.4
  - Persistent volume для данных
  - Автоматические health checks
- **redis**: Redis 7-alpine
  - Используется для rate limiting и кэширования

### Порты

- **Локальная разработка** (c `docker-compose.dev.yml`):
  - **8080**: Backend HTTP REST API
  - **8000**: AI Service HTTP (health/metrics)
  - **9090**: AI Service gRPC
  - **5433**: PostgreSQL (localhost)
  - **6379**: Redis (localhost)
- **Deployment** (только `docker-compose.yml`):
  - **8080**: Backend HTTP REST API
  - Redis/PostgreSQL/AI Service — **internal-only** (без published ports)
- **9999**: Prometheus (observability stack)
- **3000**: Grafana (observability stack)

### Docker Compose команды

> При первом запуске `docker compose` сервис `db-migrate` автоматически применит Liquibase-миграции к PostgreSQL, и только потом поднимется `backend`.
> Если `backend` не стартует — сначала проверьте логи миграций: `docker compose logs -f db-migrate`.

```bash
# Запуск всех сервисов
docker compose up -d

# Запуск с пересборкой
docker compose up --build -d

# Остановка
docker compose down

# Остановка с удалением volumes
docker compose down -v

# Просмотр логов
docker compose logs -f [service-name]

# Проверка статуса
docker compose ps
```

## 🧪 Тестирование

### Backend Tests

```bash
cd services/backend

# Запуск всех тестов
mvn test

# Запуск тестов с отчетом о покрытии
mvn test jacoco:report

# Интеграционные тесты (использует Testcontainers)
mvn verify

# Посмотреть отчет о покрытии
open target/site/jacoco/index.html
```

**Типы тестов:**
- **Unit tests** - тестирование бизнес-логики
- **Integration tests** - тестирование с реальной БД через Testcontainers
- **Security tests** - тестирование JWT аутентификации и авторизации
- **API tests** - тестирование REST endpoints

### AI Service Tests

```bash
cd services/ai-service

# Установить зависимости для тестирования
pip install pytest pytest-asyncio pytest-mock

# Запуск тестов
pytest

# С coverage
pytest --cov=app --cov-report=html

# Посмотреть отчет
open htmlcov/index.html
```

## 📊 Мониторинг

### Встроенный мониторинг

- **Health checks** для всех сервисов (Backend, AI Service, PostgreSQL, Redis)
- **Spring Boot Actuator** - метрики JVM, HTTP запросов, кэша, БД
- **Prometheus metrics endpoint** - `/actuator/prometheus`
- **Docker health checks** с автоматическим перезапуском при сбое

### Observability Stack (опционально)

Для продвинутого мониторинга можно запустить Prometheus + Grafana:

```bash
cd infra

# Запуск observability stack
docker compose -f observability-compose.yml up -d

# Остановка
docker compose -f observability-compose.yml down
```

**Доступные дашборды:**
- Grafana UI: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9091

**Метрики:**
- JVM память и GC
- HTTP запросы (latency, throughput, errors)
- Database connection pool
- gRPC вызовы
- Cache hit/miss ratio
- Rate limiting statistics

## 🚨 Troubleshooting

### Проблемы с gRPC

**Симптомы:** Backend не может подключиться к AI Service
```bash
# 1. Проверить, что AI service запущен
docker ps | grep ai-service

# 2. Проверить логи AI service
cd infra && docker compose logs -f ai-service

# 3. Проверить health endpoint
curl http://localhost:8000/healthz

# 4. Проверить, что gRPC порт открыт
telnet localhost 9090

# 5. Проверить переменные окружения в backend
docker exec backend env | grep GRPC
```

### Проблемы с Backend

**Симптомы:** Backend не отвечает или возвращает 500
```bash
# 1. Проверить логи backend
cd infra && docker compose logs -f backend

# 2. Проверить health endpoint
curl http://localhost:8080/actuator/health

# 3. Проверить подключение к БД
curl http://localhost:8080/actuator/health | jq '.components.db'

# 4. Проверить подключение к Redis
curl http://localhost:8080/actuator/health | jq '.components.redis'
```

### Проблемы с PostgreSQL

> В Docker-окружении миграции Liquibase накатываются автоматически сервисом `db-migrate` перед стартом `backend`.

**Симптомы:** Backend не может подключиться к БД
```bash
# 1. Проверить, что PostgreSQL запущен
docker ps | grep postgres

# 2. Проверить логи PostgreSQL
docker logs game-recommender-postgres-db

# 3. Подключиться к БД напрямую
docker exec -it game-recommender-postgres-db psql -U postgres -d game_recommender_ai_db

# 4. Проверить миграции Liquibase
cd infra && docker compose logs -f db-migrate
```

### Пересборка

```bash
# Полная пересборка всех сервисов
cd infra
docker compose down
docker compose build --no-cache
docker compose up -d

# Пересборка с очисткой кэша
docker compose down -v
docker compose build --no-cache
docker compose up -d

# Очистка всего Docker
docker system prune -a --volumes
```

### Проверка портов

```bash
# Проверить, какие порты заняты
lsof -i :8080  # Backend
lsof -i :8000  # AI Service HTTP
lsof -i :9090  # AI Service gRPC
lsof -i :5433  # PostgreSQL
lsof -i :6379  # Redis
```

## 🗄️ База данных

### Миграции

Используется **Liquibase** для версионирования схемы БД:
> В Docker (`infra/docker-compose.yml`) миграции накатываются автоматически отдельным сервисом `db-migrate`.
> Команды ниже полезны для ручного запуска миграций локально (например, при разработке без Docker).

```bash
cd services/backend

# Применить миграции
mvn liquibase:update

# Откатить последнюю миграцию
mvn liquibase:rollback -Dliquibase.rollbackCount=1

# Посмотреть статус миграций
mvn liquibase:status
```

## 📝 Roadmap

### Реализовано ✅
- [x] JWT аутентификация (Access + Refresh токены)
- [x] Steam OAuth интеграция
- [x] PostgreSQL с Liquibase миграциями
- [x] Redis для rate limiting и кэширования
- [x] gRPC коммуникация между Backend и AI Service
- [x] DeepSeek AI интеграция
- [x] Rate Limiting через Bucket4j
- [x] Spring Security с ролями
- [x] Docker Compose для разработки
- [x] Swagger/OpenAPI документация
- [x] Health checks и metrics
- [x] Checkstyle и JaCoCo

### В разработке 🚧
- [ ] Frontend интеграция (React UI)
- [ ] Полная интеграция Steam API (библиотека игр)
- [ ] Улучшение AI рекомендаций
- [ ] Кэширование рекомендаций

### Планируется 📋
- [ ] История рекомендаций
- [ ] Избранные игры
- [ ] Обратная связь по рекомендациям
- [ ] Рекомендации на основе библиотеки Steam
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Deployment
- [ ] E2E тесты
- [ ] Миграция на BentoML для AI Service

## 🤝 Вклад в проект

1. Fork репозитория
2. Создать feature branch
3. Внести изменения
4. Добавить тесты
5. Создать Pull Request

## 📄 Лицензия

MIT License
