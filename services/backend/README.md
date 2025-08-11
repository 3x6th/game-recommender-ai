# Game Recommender AI

Сервис для рекомендации игр с использованием DeepSeek AI.

## Настройка

### 1. Получите API ключ DeepSeek

1. Зарегистрируйтесь на [DeepSeek](https://platform.deepseek.com/)
2. Получите API ключ в разделе API Keys

### 2. Настройте переменные окружения

Создайте файл `.env` в корне проекта или установите переменную окружения:

```bash
export DEEPSEEK_API_KEY=your-api-key-here
```

### 3. Запуск приложения

```bash
mvn spring-boot:run
```

Приложение будет доступно по адресу: http://localhost:8080

## API Endpoints

### Получить рекомендации игр

```http
POST /api/games/recommend
Content-Type: application/json

{
  "preferences": "Ищу игру в жанре RPG с открытым миром, похожую на Skyrim"
}
```

### Чат с AI

```http
POST /api/games/chat
Content-Type: application/json

{
  "preferences": "Расскажи о последних играх 2024 года"
}
```

### Проверка здоровья сервиса

```http
GET /api/games/health
```

## Конфигурация

Основные настройки в `application.properties`:

- `deepseek.api.url` - URL API DeepSeek
- `deepseek.model` - модель для использования
- `deepseek.max-tokens` - максимальное количество токенов
- `deepseek.temperature` - температура генерации (0.0 - 1.0)

## Структура проекта

```
src/main/java/ru/perevalov/gamerecommenderai/
├── config/                    # Конфигурация
│   ├── DeepSeekConfig.java   # Настройки DeepSeek
│   ├── OpenApiConfig.java    # Конфигурация OpenAPI
│   └── WebClientConfig.java  # Конфигурация HTTP клиента
├── controller/               # REST контроллеры
│   └── GameRecommendationController.java
├── dto/                     # Data Transfer Objects
│   ├── DeepSeekRequest.java
│   ├── DeepSeekResponse.java
│   ├── GameRecommendationRequest.java
│   └── GameRecommendationResponse.java
├── service/                 # Бизнес-логика
│   └── DeepSeekService.java
└── GameRecommenderAiApplication.java
```

## Технологии

- Spring Boot 3.5.4
- Spring WebFlux (для HTTP клиента)
- Lombok
- H2 Database (для разработки)
- DeepSeek AI API 