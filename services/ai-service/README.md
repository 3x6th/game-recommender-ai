# AI Service - Python gRPC + FastAPI

Python AI сервис для рекомендаций игр, использующий gRPC для взаимодействия с Java backend и FastAPI для health checks.

## 🏗️ Архитектура

```
AI Service
├── gRPC Server (порт 9090)     # Основной API для Java backend
├── FastAPI Server (порт 8000)  # Health checks и метрики
└── AI Providers
    ├── DeepSeek AI             # Основной провайдер
    └── GigaChat                # Альтернативный провайдер
```

## 🚀 Быстрый старт

### 0. Установка Poetry (если не установлен)

```bash
# Установка через pip
pip install poetry

# Или через Homebrew (macOS)
brew install poetry

# Проверка установки
poetry --version
```

### 1. Установка зависимостей

```bash
# Poetry автоматически создает и управляет виртуальным окружением
poetry install

# Активация виртуального окружения (опционально)
poetry shell
```

### 2. Настройка переменных окружения

Скопируйте `env.example` в `.env` и настройте API ключи:

```bash
cp env.example .env
```

Отредактируйте `.env` файл:

```env
# AI Service API Keys
DEEPSEEK_API_KEY=sk-your-deepseek-api-key-here
GIGACHAT_API_KEY=your-gigachat-api-key-here

# Service Configuration
GRPC_PORT=9090
HTTP_PORT=8000
BACKEND_PORT=8080
```

### 3. Генерация gRPC кода

```bash
# Создание папки proto (если не существует)
mkdir -p proto

# Генерация Python кода из proto файлов
python -m grpc_tools.protoc \
    -I./../../contracts/proto \
    --python_out=./proto \
    --grpc_python_out=./proto \
    ./../../contracts/proto/reco.proto
```

### 3.1 Генерация с использованием Poetry

```bash
poetry run python -m grpc_tools.protoc \
    -I./../../contracts/proto \
    --python_out=./proto \
    --grpc_python_out=./proto \
    ./../../contracts/proto/reco.proto
```

### 3.2 Генерация на macOS с использованием pipx

```bash
brew install pipx

pipx install grpcio-tools

pipx run grpcio-tools \
    -I./../../contracts/proto \
    --python_out=./proto \
    --grpc_python_out=./proto \
    ./../../contracts/proto/reco.proto
```

### 4. Запуск сервиса

```bash
# Запуск через Poetry (рекомендуется)
poetry run uvicorn app.main:create_app_with_lifespan --host 0.0.0.0 --port 8000 --reload

# Или через Poetry shell
poetry shell
uvicorn app.main:create_app_with_lifespan --host 0.0.0.0 --port 8000 --reload

# Или через Python модуль
poetry run python -m app.main
```

## 📡 API Endpoints

### gRPC API (порт 9090)

#### GameRecommenderService

- **RecommendGames** - получение рекомендаций игр с учётом Steam библиотеки

### HTTP API (порт 8000)

- **GET** `/healthz` - проверка здоровья сервиса
- **GET** `/metrics` - базовые метрики
- **GET** `/` - информация о сервисе

## 🎯 Poetry команды

### Основные команды

```bash
# Установка зависимостей
poetry install

# Добавление новой зависимости
poetry add package-name

# Добавление dev зависимости
poetry add --group dev package-name

# Удаление зависимости
poetry remove package-name

# Обновление зависимостей
poetry update

# Показать установленные пакеты
poetry show

# Активировать виртуальное окружение
poetry shell

# Запуск команды в виртуальном окружении
poetry run command
```

## 🔧 Конфигурация

### Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|---------------|
| `DEEPSEEK_API_KEY` | API ключ DeepSeek | - |
| `GIGACHAT_API_KEY` | API ключ GigaChat | - |
| `GRPC_PORT` | Порт gRPC сервера | 9090 |
| `HTTP_PORT` | Порт FastAPI сервера | 8000 |
| `GRPC_HOST` | Хост gRPC сервера | [::] |

### Структура проекта

```
services/ai-service/
├── app/
│   ├── __init__.py
│   ├── main.py              # Главный файл приложения
│   ├── grpc_server.py       # gRPC сервер
│   ├── http_api.py          # FastAPI endpoints
│   └── services/
│       ├── __init__.py
│       ├── base.py          # Базовый класс AI сервиса
│       ├── deepseek_service.py  # DeepSeek интеграция
│       ├── gigachat_service.py  # GigaChat интеграция
│       └── registry.py      # Реестр AI сервисов
├── proto/                   # Сгенерированные gRPC файлы
├── pyproject.toml          # Poetry конфигурация
├── poetry.lock             # Lock файл зависимостей
├── .env                     # Переменные окружения
├── test_service.py          # Тесты сервиса
└── README.md               # Этот файл
```

## 🧪 Тестирование

### Запуск тестов

```bash
# Тест AI сервиса
poetry run python test_service.py

# Тест через pytest
poetry run pytest test_service.py -v
```

### Тестирование gRPC

```bash
# Проверка gRPC сервера
grpcurl -plaintext localhost:9090 list

# Тест рекомендаций
grpcurl -plaintext -d '{"userMessage": "action RPGs"}' \
    localhost:9090 gamerecommender.GameRecommenderService/RecommendGames
```

## 🔌 Интеграция с Java Backend

### gRPC контракт

Сервис реализует интерфейс `GameRecommenderService` из `contracts/proto/reco.proto`:

```protobuf
service GameRecommenderService {
  rpc RecommendGames(FullAiContextRequestProto) returns (RecommendationResponse);
}
```

### Конфигурация Java

В Java backend настройте gRPC клиент:

```properties
# application.properties
grpc.ai-service.host=localhost
grpc.ai-service.port=9090
```

## 🚀 Развертывание

### Локальная разработка

```bash
# Запуск с автоперезагрузкой
poetry run uvicorn app.main:create_app_with_lifespan --host 0.0.0.0 --port 8000 --reload

# Запуск в фоне
nohup poetry run uvicorn app.main:create_app_with_lifespan --host 0.0.0.0 --port 8000 &
```

### Docker

```bash
# Сборка образа
docker build -t ai-service .

# Запуск контейнера
docker run -p 8000:8000 -p 9090:9090 \
    -e DEEPSEEK_API_KEY=your-key \
    ai-service
```

### Docker Compose

```yaml
# docker-compose.yml
services:
  ai-service:
    build: .
    ports:
      - "8000:8000"  # FastAPI
      - "9090:9090"  # gRPC
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
    volumes:
      - ./proto:/app/proto
```

## 🔍 Мониторинг

### Health Check

```bash
curl http://localhost:8000/healthz
```

Ответ:
```json
{
  "status": "ok",
  "timestamp": "2025-08-11T14:30:00",
  "service": "ai-service",
  "version": "1.0.0"
}
```

### Метрики

```bash
curl http://localhost:8000/metrics
```


## 🐛 Troubleshooting

### Проблемы с gRPC

1. **Порт 9090 занят**
   ```bash
   lsof -i :9090
   kill -9 <PID>
   ```

2. **Proto файлы не сгенерированы**
   ```bash
   python -m grpc_tools.protoc --help
   pip install grpcio-tools
   ```

### Проблемы с API ключами

1. **Проверьте .env файл**
   ```bash
   cat .env
   ```

2. **Проверьте переменные окружения**
   ```bash
   python -c "import os; print(os.getenv('DEEPSEEK_API_KEY'))"
   ```

### Проблемы с зависимостями

1. **Обновите pip**
   ```bash
   pip install --upgrade pip
   ```

2. **Переустановите зависимости**
   ```bash
   pip uninstall -r requirements.txt -y
   pip install -r requirements.txt
   ```

## 📚 Дополнительные ресурсы

- [gRPC Python](https://grpc.io/docs/languages/python/)
- [FastAPI](https://fastapi.tiangolo.com/)
- [DeepSeek API](https://platform.deepseek.com/)
- [Python dotenv](https://pypi.org/project/python-dotenv/)


## 📄 Лицензия

Этот проект лицензирован под MIT License.
