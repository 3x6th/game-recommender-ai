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

Инструкция по конфигурации лимита в .properties:
https://github.com/MarcGiffing/bucket4j-spring-boot-starter - ссылка на документацию по библиотеке.
Там же есть инструкция по подключению баккета к различным реализациям кэша, в том числе и Redis.

`bucket4j.filters[0].id=guest-rate-limit` Идентификатор данного фильтра
`bucket4j.filters[0].cache-name=rate-limit-buckets` Идентификатор пространства имен внутри кэша, оставляем как есть
`bucket4j.filters[0].filter-order=100` Очередь в цепочке фильтров, в какую будет добавлен данный фильтр
`bucket4j.filters[0].url=/test` Регулярное выражение для указания URL-адресов, для запросов на которые будет срабатывать фильтр
`bucket4j.filters[0].strategy=first` Указывает на стратегию применения условий фильтра. Если в этом параметре указать `all`,
то можно добавить несколько `execute-condition`, тогда фильтр сработает только если все условия дадут `true`. Если указана `first`,
то условий также может быть несколько, но фильтр сработает при первом же `true`.

Настройка того, какие метрики будет отдавать фильтр. Указывается ключ `key` и для него выражение `expression`,
которое пишется на SPeL и будет выполнено, чтобы получить значение ключа.
В данном случае в первой метрике ключом будет IP, а значением IP-адрес, от которого поступил запрос.
Выражения выполняются в контексте фильтра от обертки над HttpServletRequest, именно поэтому
метод getRemoteAddr(), а не getRemoteAddress() из HttpServletRequest.
`bucket4j.filters[0].metrics.enabled=true
bucket4j.filters[0].metrics.tags[0].key=IP
bucket4j.filters[0].metrics.tags[0].expression=getRemoteAddr()
bucket4j.filters[0].metrics.tags[1].key=USER_ROLE
bucket4j.filters[0].metrics.tags[1].expression='GUEST'`

Настройка условия выполнения фильтра. Также пишется на SPeL.
`@bucketUtil.getUserRole()` - это вызов метода getUserRole() из бина BucketUtil.
Имя бина почему-то должно быть указано в стереотипной аннотации,
например, `@Component("bucketUtil")`, иначе фильтр не находит бин.
`bucket4j.filters[0].rate-limits[0].execute-condition=@bucketUtil.getUserRole().equals('GUEST')`

Также выражение на SPeL, возвращающее фильтру ключ, под которым он сохранит баккет юзера в кэш.
`bucket4j.filters[0].rate-limits[0].cache-key=@requestOnBucketRouter.getBucketCacheKey() + getRemoteAddr()`

Настройки самого баккета.
`capacity` - количество токенов в баккете.
`time` - количество времени.
`unit` - единица времени (...second, minute, hour...)
`refill-speed` - `interval` полностью обновляет состояние баккета по окончании указанного времени.
    Если баккет на 5 токенов и баккет настроен на 1 час, то если истратить токены на 10 минут,
    по прошествии с того момента как был потрачен последний токен 1 часа в баккете снова будет 5 токенов.
    `greedy` обновляет баккет постепенно. Время делится на количество токенов
    и токены добавляются в баккет постепенно в течение часа.
`id` - идентификатор настроек баккета. 
`bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=${performance.rate-limiter.role.limit.of-hour.GUEST_USER:5}
bucket4j.filters[0].rate-limits[0].bandwidths[0].time=1
bucket4j.filters[0].rate-limits[0].bandwidths[0].unit=hours
bucket4j.filters[0].rate-limits[0].bandwidths[0].refill-speed=interval
bucket4j.filters[0].rate-limits[0].bandwidths[0].id=guest-hourly`

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