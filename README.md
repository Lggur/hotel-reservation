# Hotel Reservation

Проект для бронирования отелей на Spring Boot. В репозитории четыре приложения: `eureka-server`, `api-gateway`, `hotel-service` и `booking-service`. Клиент обращается к Gateway, у `hotel-service` и `booking-service` свои базы H2.

## Состав проекта

| Модуль            | Порт | Назначение                             |
| ----------------- | ---- | -------------------------------------- |
| `eureka-server`   | 8761 | Реестр сервисов                        |
| `api-gateway`     | 8080 | Точка входа и маршрутизация            |
| `hotel-service`   | 8081 | Отели, номера, доступность, статистика |
| `booking-service` | 8082 | Пользователи, вход, бронирования       |

## Технологии

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3
- Spring Data JPA, H2 в памяти
- Spring Security, JWT
- Spring Cloud Gateway, Eureka
- Gradle 8.14.6, Kotlin DSL
- Lombok, MapStruct
- JUnit 5, MockMvc, WireMock
- springdoc-openapi (Swagger UI)

## Что нужно для запуска

- JDK 21.
- Свободные порты `8761`, `8080`, `8081`, `8082`.
- Gradle ставить не нужно, в репозитории есть wrapper.

## Как устроен проект

`hotel-service` и `booking-service` держат данные в своих базах H2 в памяти. Чужие базы сервисы не читают, всё общение идёт через REST.

`booking-service` вызывает `hotel-service`, когда нужно подтвердить или освободить номер. Общей транзакции между базами нет. Каждый сервис пишет в свою базу, а если второй шаг не удался, вызывается компенсационный `release`.

JWT выпускает `booking-service`, срок жизни 1 час, подпись HS256. Токен проверяют Gateway, `hotel-service` и `booking-service`. Для вызовов между сервисами нужен заголовок `X-Internal-Token`.

Gateway не собирает Swagger со всех сервисов и не пропускает внутренние эндпойнты наружу. Gateway и оба бизнес-сервиса регистрируются в `eureka-server`.

Решения по архитектуре записаны в ADR:

- [Отдельная БД на сервис](docs/adr/0001-database-per-service.md)
- [Сага бронирования](docs/adr/0002-booking-saga.md)
- [JWT и внутренний токен](docs/adr/0003-jwt-and-internal-token.md)
- [Маршрутизация Gateway](docs/adr/0004-gateway-routing.md)
- [Устойчивость вызовов](docs/adr/0005-resilience.md)

Gateway проверяет JWT для всех `/api/**`, кроме регистрации и входа. Он добавляет `X-Request-Id` и передаёт заголовок дальше. Сервисы проверяют токен ещё раз и пишут `requestId` в MDC.

### Маршруты Gateway

| Путь               | Сервис            |
| ------------------ | ----------------- |
| `/api/auth/**`     | `booking-service` |
| `/api/bookings/**` | `booking-service` |
| `/api/hotels/**`   | `hotel-service`   |
| `/api/rooms/**`    | `hotel-service`   |

Внутренние пути через Gateway не проходят. Вместо ответа сервиса придёт `404`:

- `/internal/**`;
- `/api/rooms/{id}/confirm-availability`;
- `/api/rooms/{id}/release`.

## Сборка и тесты

`build` собирает проект и запускает все тесты. Команды с `:module:test` нужны, если хотите прогнать только один модуль.

Windows PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat :eureka-server:test
.\gradlew.bat :hotel-service:test
.\gradlew.bat :booking-service:test
.\gradlew.bat :api-gateway:test
```

Linux и macOS:

```bash
./gradlew build
./gradlew :eureka-server:test
./gradlew :hotel-service:test
./gradlew :booking-service:test
./gradlew :api-gateway:test
```

## Запуск

Сервисы запускаются в таком порядке:

1. `.\gradlew.bat :eureka-server:bootRun`
2. `.\gradlew.bat :hotel-service:bootRun`
3. `.\gradlew.bat :booking-service:bootRun`
4. `.\gradlew.bat :api-gateway:bootRun`

После старта проверьте health:

- http://localhost:8761/actuator/health
- http://localhost:8081/actuator/health
- http://localhost:8082/actuator/health
- http://localhost:8080/actuator/health

Eureka может показать сервисы не сразу, обычно это занимает до 30 секунд.

Swagger UI можно открыть на самих сервисах:

- Hotel Service: http://localhost:8081/swagger-ui.html
- Booking Service: http://localhost:8082/swagger-ui.html

## Настройки

Переменные окружения:

| Переменная                         | Сервис                  | По умолчанию                                          |
| ---------------------------------- | ----------------------- | ----------------------------------------------------- |
| `JWT_SECRET`                       | Gateway, Hotel, Booking | `hotel-reservation-dev-secret-key-please-change-2026` |
| `INTERNAL_TOKEN`                   | Hotel, Booking          | `hotel-reservation-internal-token`                    |
| `HOTEL_SERVICE_URL`                | Booking                 | `http://localhost:8081`                               |
| `HOTEL_SERVICE_RETRY_MAX_ATTEMPTS` | Booking                 | `3`                                                   |
| `HOTEL_SERVICE_RETRY_BACKOFF`      | Booking                 | `200ms`                                               |
| `BOOKING_RECOVERY_ENABLED`         | Booking                 | `true`                                                |
| `BOOKING_RECOVERY_INTERVAL_MS`     | Booking                 | `60000`                                               |
| `BOOKING_RECOVERY_PENDING_TIMEOUT` | Booking                 | `PT10M`                                               |
| `ADMIN_USERNAME`                   | Booking                 | `admin`                                               |
| `ADMIN_PASSWORD`                   | Booking                 | `password`                                            |
| `DEMO_DATA_ENABLED`                | Hotel, Booking          | `true`                                                |

При старте `booking-service` создаёт администратора, если такого пользователя ещё нет.

Параметры CircuitBreaker лежат в `booking-service/src/main/resources/application.yml`: окно 10 вызовов, минимум 5 вызовов, порог ошибок 50%, пауза 10 секунд.

## Демо-данные

`booking-service` всегда создаёт администратора `admin` / `password`, если пользователя с таким именем нет. `DEMO_DATA_ENABLED` на это не влияет.

При `DEMO_DATA_ENABLED=true` появляются:

- пользователь `demo` / `password`;
- отели `Hotel 1` и `Hotel 2` с номерами `101`, `102`, `103`, `201` и `202`;
- подтверждённая бронь пользователя `demo` на номер `101` со сдвигом от текущей даты. На ней видно занятость, статистику и рекомендации.

Если демо-данные не нужны, поставьте `DEMO_DATA_ENABLED=false`. Администратор останется.

## API

Внешние запросы идут через Gateway: `http://localhost:8080`.

### Вход и регистрация

| Метод  | Путь                 | Доступ   |
| ------ | -------------------- | -------- |
| `POST` | `/api/auth/register` | публично |
| `POST` | `/api/auth/login`    | публично |
| `GET`  | `/api/auth/me`       | JWT      |

Регистрация:

```json
{"username": "user", "password": "password"}
```

Вход возвращает JWT:

```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 2,
  "username": "user",
  "roles": ["USER"]
}
```

Роли: `USER` и `ADMIN`.

### Бронирования

| Метод    | Путь                        | Доступ                             |
| -------- | --------------------------- | ---------------------------------- |
| `POST`   | `/api/bookings`             | JWT                                |
| `GET`    | `/api/bookings`             | JWT: свои брони, `ADMIN` видит все |
| `GET`    | `/api/bookings/{id}`        | владелец или `ADMIN`               |
| `DELETE` | `/api/bookings/{id}`        | владелец или `ADMIN`               |
| `POST`   | `/api/bookings/{id}/cancel` | владелец или `ADMIN`               |

`GET /api/bookings?page=0&size=20` отдаёт страницу:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`page` считается с нуля. `size` может быть от 1 до 100. Сортировка по `id` по убыванию.

Создание брони:

```json
{
  "hotelId": 1,
  "roomId": 10,
  "startDate": "2026-01-10",
  "endDate": "2026-01-12"
}
```

`roomId` можно не передавать. Тогда сервис сам выберет свободный номер на указанные даты. Если свободных номеров нет, вернётся `409`.

Если передать `Idempotency-Key`, повторный запрос с тем же ключом и пользователем вернёт уже созданную бронь. Номер второй раз не подтверждается. Ключ хранится в брони, максимум 128 символов.

Ответ:

```json
{
  "id": 1,
  "userId": 2,
  "hotelId": 1,
  "roomId": 10,
  "startDate": "2026-01-10",
  "endDate": "2026-01-12",
  "status": "CONFIRMED",
  "failureReason": null,
  "createdAt": "2026-01-05T12:00:00Z",
  "updatedAt": "2026-01-05T12:00:00Z"
}
```

Отмена идемпотентна. Если бронь подтверждена, сначала освобождается номер в `hotel-service`.

### Отели и номера

| Метод                   | Путь                              | Доступ          |
| ----------------------- | --------------------------------- | --------------- |
| `GET`                   | `/api/hotels`, `/api/hotels/{id}` | `USER`, `ADMIN` |
| `POST`, `PUT`, `DELETE` | `/api/hotels/**`                  | `ADMIN`         |
| `GET`                   | `/api/rooms`, `/api/rooms/{id}`   | `USER`, `ADMIN` |
| `GET`                   | `/api/rooms/recommend`            | `USER`, `ADMIN` |
| `GET`                   | `/api/rooms/statistics`           | `ADMIN`         |
| `POST`, `PUT`, `DELETE` | `/api/rooms/**`                   | `ADMIN`         |

- `GET /api/rooms?hotelId=&startDate=&endDate=&sort=&direction=` возвращает доступные номера. Если переданы даты, учитываются пересечения с бронями. `sort` принимает `id`, `number`, `hotelId`, `timesBooked`. `direction` принимает `asc` или `desc`, по умолчанию `id asc`.
- `GET /api/rooms/recommend?hotelId=&startDate=&endDate=` возвращает доступные номера по возрастанию `times_booked`, при равенстве по `id`. `startDate` и `endDate` обязательны.
- `GET /api/rooms/statistics?from=&to=&hotelId=` показывает занятость за период: активные брони, занятые ночи и коэффициент загрузки по каждому номеру.

Внутренние `confirm-availability` и `release` работают иначе: им нужен `X-Internal-Token`, а не JWT.

Создание отеля (`ADMIN`):

```json
{"name": "Grand Hotel", "address": "Main Street 1"}
```

Создание номера (`ADMIN`):

```json
{"hotelId": 1, "number": "101", "available": true}
```

### Внутренние эндпойнты

Эти пути не идут через Gateway. Их вызывают напрямую с заголовком `X-Internal-Token`:

| Метод  | Путь                                   | Сервис         |
| ------ | -------------------------------------- | -------------- |
| `POST` | `/api/rooms/{id}/confirm-availability` | Hotel (8081)   |
| `POST` | `/api/rooms/{id}/release`              | Hotel (8081)   |
| `GET`  | `/internal/bookings/{id}`              | Booking (8082) |
| `POST` | `/internal/bookings/{id}/cancel`       | Booking (8082) |
| `POST` | `/internal/bookings/recover`           | Booking (8082) |

## Что происходит при создании брони

1. Бронь сохраняется со статусом `PENDING`.
2. `booking-service` просит `hotel-service` подтвердить номер.
3. Если номер подтверждён, бронь получает статус `CONFIRMED`.
4. Если подтвердить не удалось, бронь переходит в `CANCELLED`, а причина попадает в `failureReason`:
   - `ROOM_UNAVAILABLE`: номер занят или недоступен;
   - `HOTEL_SERVICE_UNAVAILABLE`: таймаут или `5xx`;
   - `HOTEL_SERVICE_ERROR`: неожиданный ответ Hotel Service.
5. Если номер уже подтверждён, а сохранить статус локально не вышло, вызывается компенсационный `release`.

`booking-service` делает до 3 попыток вызова Hotel Service с задержкой между ними. После серии ошибок CircuitBreaker открывается, и клиент получает `503`. Если `release` вернул `404`, это нормальный результат.

Планировщик находит брони в `PENDING`, которые висят дольше `BOOKING_RECOVERY_PENDING_TIMEOUT`. Он освобождает номер и переводит бронь в `CANCELLED` с причиной `STALE_PENDING_RECOVERED`, но только если бронь всё ещё в `PENDING`. Если `release` не сработал, бронь остаётся `PENDING` до следующей попытки.

Даты брони работают по полуинтервалу `[startDate, endDate)`.

## Таблицы

Hibernate создаёт обе базы при старте (`ddl-auto: create-drop`). Данные живут в памяти процесса.

### `hotel-service`, база `hoteldb`

Таблица `hotels`:

| Поле      | Тип          | Ограничения  |
| --------- | ------------ | ------------ |
| `id`      | BIGINT       | PK, identity |
| `name`    | VARCHAR(255) | NOT NULL     |
| `address` | VARCHAR(512) | NOT NULL     |

Таблица `rooms`:

| Поле           | Тип         | Ограничения                     |
| -------------- | ----------- | ------------------------------- |
| `id`           | BIGINT      | PK, identity                    |
| `hotel_id`     | BIGINT      | FK → `hotels.id`, NOT NULL      |
| `number`       | VARCHAR(32) | NOT NULL                        |
| `available`    | BOOLEAN     | NOT NULL                        |
| `times_booked` | BIGINT      | NOT NULL, счётчик подтверждений |

Пара `(hotel_id, number)` уникальна. Ограничение называется `uk_rooms_hotel_number`.

Таблица `room_reservations`:

| Поле         | Тип          | Ограничения                                           |
| ------------ | ------------ | ----------------------------------------------------- |
| `id`         | BIGINT       | PK, identity                                          |
| `room_id`    | BIGINT       | FK → `rooms.id`, NOT NULL                             |
| `booking_id` | BIGINT       | NOT NULL, уникальный (`uk_room_reservations_booking`) |
| `request_id` | VARCHAR(128) | NULL                                                  |
| `start_date` | DATE         | NULL для tombstone-записи                             |
| `end_date`   | DATE         | NULL для tombstone-записи                             |
| `status`     | VARCHAR(16)  | NOT NULL: `ACTIVE` или `RELEASED`                     |
| `created_at` | TIMESTAMP    | NOT NULL                                              |

Индекс `idx_room_reservations_room_status_dates` по `(room_id, status, start_date, end_date)` ускоряет проверку пересечений.

### `booking-service`, база `bookingdb`

Таблица `users`:

| Поле            | Тип          | Ограничения                  |
| --------------- | ------------ | ---------------------------- |
| `id`            | BIGINT       | PK, identity                 |
| `username`      | VARCHAR(64)  | NOT NULL, уникальный         |
| `password_hash` | VARCHAR(100) | NOT NULL, BCrypt             |
| `role`          | VARCHAR(16)  | NOT NULL: `USER` или `ADMIN` |
| `created_at`    | TIMESTAMP    | NOT NULL                     |

Таблица `bookings`:

| Поле              | Тип          | Ограничения                                      |
| ----------------- | ------------ | ------------------------------------------------ |
| `id`              | BIGINT       | PK, identity                                     |
| `user_id`         | BIGINT       | NOT NULL                                         |
| `hotel_id`        | BIGINT       | NOT NULL                                         |
| `room_id`         | BIGINT       | NOT NULL                                         |
| `start_date`      | DATE         | NOT NULL                                         |
| `end_date`        | DATE         | NOT NULL                                         |
| `status`          | VARCHAR(16)  | NOT NULL: `PENDING`, `CONFIRMED` или `CANCELLED` |
| `failure_reason`  | VARCHAR(64)  | NULL                                             |
| `idempotency_key` | VARCHAR(128) | NULL                                             |
| `created_at`      | TIMESTAMP    | NOT NULL                                         |
| `updated_at`      | TIMESTAMP    | NOT NULL                                         |
| `version`         | BIGINT       | NOT NULL, оптимистичная блокировка               |

Пара `(user_id, idempotency_key)` уникальна. Индексы: `(user_id, id)` и `(status, created_at)`.

## Коды ошибок

| Статус | Причина                                             |
| ------ | --------------------------------------------------- |
| `400`  | Ошибка валидации или некорректный запрос            |
| `401`  | Нет JWT или он недействителен                       |
| `403`  | Нет прав, чужая бронь, неверный внутренний токен    |
| `404`  | Ресурс не найден или внутренний путь закрыт Gateway |
| `409`  | Номер занят, конфликт дат или дубликат              |
| `502`  | Hotel Service вернул неожиданный ответ              |
| `503`  | Hotel Service недоступен или не ответил вовремя     |
