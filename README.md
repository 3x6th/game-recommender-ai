# Game Recommender AI

Система рекомендаций игр с использованием AI, построенная на микросервисной архитектуре с поддержкой отказоустойчивости, кэширования и лимитов запросов.

## Архитектура

### Сервисы

- **AI Service** (Python/gRPC) - сервис AI для генерации рекомендаций
- **Backend Service** (Java/Spring Boot) - основной API сервис
- **Redis** - распределенное кэширование и хранение лимитов

### Ключевые компоненты

#### Resilience4j
- **Circuit Breaker** - защита от каскадных отказов
- **Retry** - автоматические повторные попытки
- **Time Limiter** - таймауты для внешних вызовов

#### Кэширование
- **Caffeine** - локальное кэширование (рекомендации, пользовательские предпочтения)
- **Redis** - распределенное кэширование (данные Steam API)

#### Лимиты запросов
- **Rate Limiting** - контроль QPS к Steam API
- **Concurrent Users** - ограничение параллельных пользователей
- **Throttling** - троттлинг запросов по времени

## Требования

- Java 21
- Python 3.11+
- Docker & Docker Compose
- Redis 7+

## Быстрый старт

### 1. Клонирование и настройка

```bash
git clone <repository>
cd game-recommender-ai
cp env.example .env
# Настройте переменные окружения в .env
```

### 2. Запуск сервисов

```bash
cd infra
make up
```

### 3. Проверка статуса

```bash
make status
make health
```

## Конфигурация

### Resilience4j

```properties
# Circuit Breaker
resilience4j.circuitbreaker.instances.grpcClient.sliding-window-size=10
resilience4j.circuitbreaker.instances.grpcClient.failure-rate-threshold=50

# Retry
resilience4j.retry.instances.grpcClient.max-attempts=3
resilience4j.retry.instances.grpcClient.wait-duration=1s

# Time Limiter
resilience4j.timelimiter.instances.grpcClient.timeout-duration=10s
```

### Кэширование

```properties
# Caffeine (локальное)
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=1h

# Redis (распределенное)
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Лимиты запросов

```properties
# Steam API
app.rate-limit.steam-api.max-requests-per-second=10
app.rate-limit.steam-api.max-requests-per-minute=100
app.rate-limit.steam-api.max-requests-per-hour=1000

# Пользователи
app.rate-limit.concurrent-users.max=100
```

## API Endpoints

### Рекомендации игр
```
POST /api/games/recommendations
{
  "preferences": "action, adventure, open world"
}
```

### Чат с AI
```
POST /api/chat
{
  "message": "Какие игры похожи на GTA V?"
}
```

### Steam API интеграция
```
GET /api/steam/games/{gameId}
GET /api/steam/users/{userId}/games
```

## Мониторинг

### Health Checks
- `/actuator/health` - общее состояние сервиса
- `/actuator/health/grpcClient` - состояние gRPC клиента
- `/actuator/health/redis` - состояние Redis

### Метрики
- `/actuator/metrics` - Prometheus метрики
- `/actuator/prometheus` - экспорт метрик

### Логирование
```bash
make logs-backend    # Логи backend
make logs-ai         # Логи AI сервиса
make logs-redis      # Логи Redis
```

## Управление

### Основные команды
```bash
make up              # Запуск
make down            # Остановка
make restart         # Перезапуск
make build           # Пересборка
make clean           # Очистка
```

### Мониторинг
```bash
make status          # Статус сервисов
make health          # Проверка здоровья
make monitor         # Мониторинг ресурсов
```

## Производительность

### Критерии
- **N параллельных логинов**: 100 пользователей
- **QPS к Steam API**: 10 запросов/сек
- **Время отклика**: < 2 секунды
- **Доступность**: 99.9%

### Стратегии оптимизации
1. **Кэширование** - снижение нагрузки на внешние API
2. **Circuit Breaker** - быстрый отказ при недоступности
3. **Retry** - автоматическое восстановление
4. **Rate Limiting** - контроль нагрузки
5. **Connection Pooling** - переиспользование соединений

## Разработка

### Тестирование
```bash
make test            # Запуск всех тестов
cd services/backend && ./mvnw test
cd services/ai-service && python -m pytest
```

### Локальная разработка
```bash
# Backend
cd services/backend
./mvnw spring-boot:run

# AI Service
cd services/ai-service
python -m uvicorn app.main:app --reload

# Redis
docker run -d -p 6379:6379 redis:7-alpine
```

## Тестирование системы

### 1. Проверка Resilience4j

```bash
# Запустите тесты
cd services/backend
./mvnw test -Dtest=Resilience4jTest

# Проверьте Circuit Breaker
curl http://localhost:8080/actuator/health/grpcClient
```

### 2. Проверка кэширования

```bash
# Первый запрос (без кэша)
curl -X POST http://localhost:8080/api/games/recommendations \
  -H "Content-Type: application/json" \
  -d '{"preferences": "action games"}'

# Второй запрос (из кэша)
curl -X POST http://localhost:8080/api/games/recommendations \
  -H "Content-Type: application/json" \
  -d '{"preferences": "action games"}'
```

### 3. Проверка Rate Limiting

```bash
# Симуляция множественных запросов
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/chat \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"test message $i\"}"
  echo "Request $i completed"
  sleep 0.1
done
```

### 4. Проверка Redis

```bash
# Подключение к Redis
make redis-cli

# В Redis CLI
KEYS *
INFO memory
```

## Troubleshooting

### Частые проблемы

1. **Redis недоступен**
   ```bash
   make logs-redis
   docker-compose exec redis redis-cli ping
   ```

2. **gRPC соединение**
   ```bash
   make logs-backend
   curl http://localhost:8080/actuator/health/grpcClient
   ```

3. **Rate Limiting**
   ```bash
   make logs-backend | grep "Rate limit"
   ```

### Логи и отладка
```bash
# Подробные логи
docker-compose logs -f --tail=100

# Отладка конкретного сервиса
docker-compose exec backend sh
docker-compose exec ai-service sh
```

## Метрики и мониторинг

### Prometheus метрики

```bash
# Получение метрик
curl http://localhost:8080/actuator/prometheus

# Основные метрики
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
curl http://localhost:8080/actuator/metrics/cache.gets
```

### Логирование

```bash
# Фильтрация логов по компонентам
make logs-backend | grep "CircuitBreaker"
make logs-backend | grep "Rate limit"
make logs-backend | grep "Cache"
```

## Производительность в продакшене

### Настройки JVM

```bash
# Оптимизированные настройки для продакшена
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Мониторинг Redis

```bash
# Проверка производительности Redis
docker-compose exec redis redis-cli --latency
docker-compose exec redis redis-cli --latency-history
```

### Нагрузочное тестирование

```bash
# Установка Apache Bench
sudo apt-get install apache2-utils

# Тест производительности
ab -n 1000 -c 10 -H "Content-Type: application/json" \
   -p test-data.json \
   http://localhost:8080/api/games/recommendations
```
