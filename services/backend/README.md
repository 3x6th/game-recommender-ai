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
Запустить докер и в директроии файла prometheus.yml ввести команды:
```bash
docker network create PCAI_monitoring
```

```bash
docker run -d \
  --name PCAI_prometheus \
  --network GR_monitoring \
  -p 9999:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

```bash
docker run -d \
  --name PCAI_grafana \
  --network GR_monitoring \
  -p 3000:3000 \
  grafana/grafana
```

В Grafana добавляем data source - prometheus с URL: http://host.docker.internal:9999
