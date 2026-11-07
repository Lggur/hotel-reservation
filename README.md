# Hotel Reservation

Микросервисное приложение для бронирования отелей.

## Компоненты

| Модуль | Порт | Назначение |
|---|---|---|
| `eureka-server` | 8761 | Реестр и обнаружение сервисов |
| `api-gateway` | 8080 | Единая точка входа, маршрутизация |
| `hotel-service` | 8081 | Отели, номера, доступность, статистика |
| `booking-service` | 8082 | Пользователи, аутентификация, бронирования |

## Стек

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3
- Spring Data JPA + H2 (in-memory)
- Spring Security + JWT
- Spring Cloud Gateway, Eureka
- Gradle 8.14.6 (Kotlin DSL)
- Lombok, MapStruct
- JUnit 5, MockMvc, WireMock
- springdoc-openapi (Swagger UI)

## Сборка

```powershell
.\gradlew.bat build
```

## Запуск

Порядок запуска:

1. `.\gradlew.bat :eureka-server:bootRun`
2. `.\gradlew.bat :hotel-service:bootRun`
3. `.\gradlew.bat :booking-service:bootRun`
4. `.\gradlew.bat :api-gateway:bootRun`

Swagger UI:

- Hotel Service: http://localhost:8081/swagger-ui.html
- Booking Service: http://localhost:8082/swagger-ui.html

## Статус разработки

Реализация ведётся поэтапно. Документация будет дополняться по мере готовности
функциональности.
