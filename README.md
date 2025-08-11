# PlayCure - Game Recommendation AI Platform

Монорепо с Java Spring Boot backend и Python AI сервисом, использующий gRPC для коммуникации.

## 🏗️ Архитектура

```
game-recommender-ai/
├── contracts/           # Общие контракты
│   └── proto/          # gRPC proto файлы
├── services/            # Микросервисы
│   ├── backend/        # Java Spring Boot (порт 8080)
│   └── ai-service/     # Python gRPC + FastAPI (порты 8000, 9090)
└── infra/              # Инфраструктура
    ├── docker-compose.yml
    └── Makefile
```

## 🚀 Быстрый старт

### Предварительные требования

- Docker и Docker Compose
- Java 21 (для локальной разработки)
- Python 3.11 (для локальной разработки)
- Make (опционально)

### 1. Клонирование и настройка

```bash
git clone <repository-url>
cd PlayCure

# Скопировать переменные окружения
cp env.example .env

# Отредактировать .env файл с вашими API ключами
nano .env
```

### 2. Запуск с Docker Compose

```bash
cd infra

# Сгенерировать gRPC код и запустить сервисы
make proto.gen
make up

# Проверить статус
make status

# Посмотреть логи
make logs
```

### 3. Тестирование

```bash
# Проверить health endpoints
make test

# Или вручную:
curl http://localhost:8000/healthz          # AI Service health
curl http://localhost:8080/actuator/health  # Backend health
curl http://localhost:8080/api/games/test   # Test gRPC connection
```

## 🔧 Разработка

### Генерация gRPC кода

```bash
# Python
make proto.gen.py

# Java
make proto.gen.java

# Все языки
make proto.gen
```

### Локальная разработка

#### Backend (Java)
```bash
cd services/backend
mvn spring-boot:run
```

#### AI Service (Python)
```bash
cd services/ai-service
pip install -r requirements.txt
python -m app.main
```

### Полезные команды

```bash
make help      # Показать все доступные команды
make up        # Запустить сервисы
make down      # Остановить сервисы
make restart   # Перезапустить сервисы
make clean     # Очистить все контейнеры и образы
make logs      # Показать логи всех сервисов
make test      # Протестировать сервисы
```

## 📡 API Endpoints

### Backend (Java Spring Boot)
- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`
- **Game Recommendations**: `POST /api/games/recommend`
- **Chat**: `POST /api/games/chat`
- **Test gRPC**: `GET /api/games/test`

### AI Service (Python)
- **Health**: `GET /healthz`
- **Metrics**: `GET /metrics`
- **gRPC**: `localhost:9090`

## 🔌 gRPC API

### GameRecommenderService

```protobuf
service GameRecommenderService {
  rpc Recommend(RecommendationRequest) returns (RecommendationResponse);
  rpc Chat(ChatRequest) returns (ChatResponse);
}
```

### Примеры использования

#### Java Client
```java
@Autowired
private GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub stub;

RecommendationRequest request = RecommendationRequest.newBuilder()
    .setPreferences("action games with good story")
    .setMaxRecommendations(5)
    .build();

RecommendationResponse response = stub.recommend(request);
```

#### Python Client
```python
import grpc
from app.proto import reco_pb2, reco_pb2_grpc

channel = grpc.insecure_channel('localhost:9090')
stub = reco_pb2_grpc.GameRecommenderServiceStub(channel)

request = reco_pb2.RecommendationRequest(
    preferences="action games with good story",
    max_recommendations=5
)

response = stub.Recommend(request)
```

## 🤖 AI Providers

### Поддерживаемые провайдеры

- **DeepSeek** - основной AI провайдер
- **GigaChat** - альтернативный провайдер

### Конфигурация

```bash
# В .env файле
DEEPSEEK_API_KEY=your-deepseek-api-key
GIGACHAT_API_KEY=your-gigachat-api-key
```

## 🐳 Docker

### Образы

- **backend**: Java 21 + Spring Boot
- **ai-service**: Python 3.11 + gRPC + FastAPI

### Порты

- **8080**: Backend HTTP API
- **8000**: AI Service HTTP (health/metrics)
- **9090**: AI Service gRPC

## 🔮 BentoML готовность

Архитектура подготовлена для будущей миграции на BentoML:

- **Стабильный gRPC контракт** - `reco.proto` остается неизменным
- **Переиспользуемая логика провайдеров** - в `app/services/`
- **Простая замена** - заменить Python entrypoint на `bentoml serve-grpc`

## 🧪 Тестирование

```bash
# Backend tests
cd services/backend
mvn test

# AI Service tests
cd services/ai-service
pytest
```

## 📊 Мониторинг

- **Health checks** для всех сервисов
- **Prometheus метрики** в backend
- **Базовые метрики** в AI service
- **Docker health checks** с автоматическим перезапуском

## 🚨 Troubleshooting

### Проблемы с gRPC

1. Проверить, что AI service запущен на порту 9090
2. Проверить логи: `make logs.ai`
3. Проверить health endpoint: `curl http://localhost:8000/healthz`

### Проблемы с Backend

1. Проверить логи: `make logs.backend`
2. Проверить health endpoint: `curl http://localhost:8080/actuator/health`
3. Проверить gRPC соединение: `curl http://localhost:8080/api/games/test`

### Пересборка

```bash
make rebuild  # Полная пересборка всех сервисов
```

## 📝 TODO

- [ ] Реализовать реальные API вызовы к DeepSeek
- [ ] Реализовать реальные API вызовы к GigaChat
- [ ] Добавить аутентификацию
- [ ] Добавить rate limiting
- [ ] Улучшить метрики и мониторинг
- [ ] Добавить CI/CD pipeline
- [ ] Подготовить к продакшену

## 🤝 Вклад в проект

1. Fork репозитория
2. Создать feature branch
3. Внести изменения
4. Добавить тесты
5. Создать Pull Request

## 📄 Лицензия

MIT License
