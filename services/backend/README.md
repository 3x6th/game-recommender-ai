# Game Recommender AI Backend

## Архитектура

Backend сервис построен на Spring Boot с использованием gRPC для коммуникации с AI сервисом.

## gRPC Архитектура

### Spring Boot Starters
Проект использует `net.devh` Spring Boot gRPC Starters для упрощения работы с gRPC:

- **grpc-client-spring-boot-starter** - для gRPC клиента

### Компоненты

#### 1. GameRecommenderGrpcClient
Отдельный клиент для работы с gRPC AI сервисом:
- Инкапсулирует всю логику gRPC коммуникации
- Использует `@GrpcClient("ai-service")` для автоматической конфигурации
- Обрабатывает ошибки и логирование

#### 2. GameRecommenderService
Бизнес-логика сервиса:
- Использует `GameRecommenderGrpcClient` для коммуникации
- Преобразует gRPC ответы в DTO
- Обрабатывает бизнес-логику

#### 3. GameRecommendationController
REST контроллер:
- Предоставляет HTTP API для получения рекомендаций
- Включает health check для gRPC соединения

## Конфигурация

### application.properties
```properties
# gRPC Client Configuration
grpc.client.ai-service.address=localhost:9090
grpc.client.ai-service.negotiationType=plaintext
grpc.client.ai-service.deadline=30s
```

### Инструкция по конфигурированию лимитов по ролям

В проекте используется распределённый rate limiting через **Redis + Bucket4j**:

- **Входящий rate limiting для API**: `ru.perevalov.gamerecommenderai.filter.RateLimitWebFilter`
  - Сохраняет состояние лимитов в Redis (разделяется между инстансами).
  - Ключи строятся по роли/сессии/steamId (или IP fallback).
  - Лимиты настраиваются в `application.properties`:
    - `performance.rate-limiter.role.limit.of-hour.GUEST_USER`
    - `performance.rate-limiter.role.limit.of-hour.USER`
- **Глобальный rate limiting для Steam API** (исходящие запросы): `ru.perevalov.gamerecommenderai.config.WebClientConfig`
  - Использует Bucket4j bucket из `ru.perevalov.gamerecommenderai.config.redis.RateLimitConfig`.

### Зависимости
- Spring Boot 3.2+
- gRPC Spring Boot Starter 2.15.0
- Protobuf Maven Plugin для генерации кода

## Запуск

```bash
mvn spring-boot:run
```

## Тестирование

```bash
mvn test
```

## Структура проекта

```
src/main/java/ru/perevalov/gamerecommenderai/
├── client/
│   └── GameRecommenderGrpcClient.java
├── controller/
│   └── GameRecommendationController.java
├── dto/
│   ├── GameRecommendation.java
│   ├── GameRecommendationRequest.java
│   └── GameRecommendationResponse.java
├── exception/
│   └── GameRecommenderException.java
└── service/
    └── GameRecommenderService.java
``` 

## Метрики Prometheus и Grafana

Необходимы docker images: prom/prometheus и grafana/grafana
Запустить докер и в директроии /infra ввести команду:
```bash
docker compose -f observability-compose.yml up --build
```

В Grafana добавляем data source - prometheus с URL: http://host.docker.internal:9999
Импортируем дашборды из директории infra/grafana
Вместо импората dashboard-jvm.json можно загрузить этот дашборд по id 4701
