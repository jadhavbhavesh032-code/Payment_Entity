# Payment Module

This repository currently contains the Payment module only: `Payment`,
`PaymentTransaction`, and `Refund`. The API base URL is
`http://localhost:9080/api/payments` unless `SERVER_PORT` is overridden.

## Runtime profiles

### Local profile (explicit opt-in)

The optional `local` profile uses a file-backed H2 database under `data/` and
does not require PostgreSQL:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile is intended for development. It uses Hibernate schema update
and does not execute the PostgreSQL Flyway migrations.

### PostgreSQL profile

The default `postgres` profile runs Flyway before Hibernate validates the schema. Set
the following environment variables for the target database:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/payment_entity_db` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | Empty; supply this for password-protected databases |

```powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
$env:DB_URL = "jdbc:postgresql://localhost:5432/payment_entity_db"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your PostgreSQL password>"
.\mvnw.cmd spring-boot:run
```

Do not commit database passwords or gateway credentials.

Alternatively run `.\scripts\Start-Payment.ps1`; it prompts for the password
without displaying it and restores the calling process environment on exit.

### Starting from VS Code

Open **Run and Debug**, select **Payment Entity (PostgreSQL)**, then press **F5**
and enter the PostgreSQL password in the masked prompt. Launch configurations
are provided for opening either the outer `Payment_Entity` folder or the inner
`movie-booking-system` folder. The password is supplied to that application
process and is not saved in the launch configuration.

If startup reports `The server requested SCRAM-based authentication, but no
password was provided`, the Java process has not received `DB_PASSWORD`. Use
the named launch configuration above or the PowerShell startup script. A
password set temporarily in another terminal is not inherited by VS Code.

Run only one application instance on port 9080. Stop the previous run before
starting another; otherwise startup will fail because the port is already in use.

### Swagger and Postman verification

Open <http://localhost:9080/swagger-ui/index.html>. The JSON specification is
at <http://localhost:9080/v3/api-docs>. Springdoc 3.2.0 supports this project's
Spring Boot 4.1.x version (https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
Expand a POST and use **Try it out**. Create a payment first, copy its paymentId,
then use it in the transaction/refund path. Supply the displayed required
`Idempotency-Key`; retain it for retries and change it for new logical requests.
The gateways are mocks, so no real money moves.

Import `postman/PaymentEntity.postman_collection.json` into Postman and run
the collection in order. The collection generates a unique booking ID and keys
for every run. It retains its test financial records for inspection.
The same collection runs locally using Postman's Newman runner:

```powershell
npx.cmd --yes newman@6 run postman/PaymentEntity.postman_collection.json --reporters cli,json --reporter-json-export target/postman-results.json
```

Regenerate the collection with `python scripts/generate_postman.py` after editing
the collection generator. No passwords are stored in the collection.

### Automated tests

` .\mvnw.cmd test ` uses in-memory H2 and does not run the PostgreSQL SQL files.
For real migrations and API tests, create a dedicated `payment_entity_test`
database, set `DB_PASSWORD`, then run:

```powershell
.\mvnw.cmd '-Dspring.profiles.active=postgres-test' test
```

The `postgres-test` profile uses `payment_entity_test` by default; override only
with `PAYMENT_TEST_DB_URL` pointing to a disposable test database. Never point
these tests at the application database. Tests retain their own generated records.

## API endpoints

The completed application surface contains eleven domain-safe endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/payments` | Create a payment for a booking |
| `GET` | `/api/payments` | List payments with optional filters and pagination |
| `GET` | `/api/payments/{paymentId}` | Get a payment by ID |
| `GET` | `/api/payments/booking/{bookingId}` | Get the payment for a booking |
| `POST` | `/api/payments/{paymentId}/cancel` | Cancel an eligible payment |
| `POST` | `/api/payments/{paymentId}/transactions` | Record and process a payment attempt |
| `GET` | `/api/payments/{paymentId}/transactions` | Get ordered transaction history |
| `GET` | `/api/payments/{paymentId}/transactions/{transactionId}` | Get a transaction belonging to a payment |
| `POST` | `/api/payments/{paymentId}/refunds` | Record and process a refund attempt |
| `GET` | `/api/payments/{paymentId}/refunds` | Get ordered refund history |
| `GET` | `/api/payments/{paymentId}/refunds/{refundId}` | Get a refund belonging to a payment |

Nested transaction and refund reads return `404 Not Found` when the child does
not belong to the payment in the path.

### Payment listing

`GET /api/payments` supports these optional query parameters:

- `userId`: positive user ID
- `paymentStatus`: a valid `PaymentStatus` value
- `page`: zero-based page number; default `0`
- `size`: positive page size; default `20`, maximum `100`
- `sort`: sortable field and direction, for example `createdAt,desc`

The response uses the module's page response DTO rather than serializing a
Spring Data `Page` directly. Invalid paging, status, or sort values return
`400 Bad Request`.

### Idempotent attempts

Both attempt-creation endpoints require a nonblank `Idempotency-Key` request
header of at most 100 characters:

- `POST /api/payments/{paymentId}/transactions`
- `POST /api/payments/{paymentId}/refunds`

A newly recorded attempt returns `201 Created`. Repeating the same logical
request with the same key returns the existing attempt with `200 OK` and does
not invoke the gateway again. Reusing a key for conflicting request data
returns `409 Conflict`.

An HTTP success response means the API recorded or retrieved the attempt; it
does not by itself mean the financial operation succeeded. Always inspect the
transaction or refund status in the response data. Response messages also name
the recorded financial outcome.

## Financial-record policy

Payment, transaction, and refund records are append-only financial history.
The API deliberately provides no generic `DELETE` endpoint and no generic
`PUT` or `PATCH` endpoint that can rewrite amounts, statuses, or transaction
history. State changes are exposed only through explicit operations such as
process, retry, cancel, and refund.

## Gateway limitation

The included payment and refund gateways are mock adapters for local
development. They are not real provider integrations and do not provide real
settlement, credentials, signed callbacks, webhook verification, or provider
reconciliation. Gateway failure simulation is not part of the public request
contract; automated tests can supply test gateway implementations later.

## External integration contracts

The `com.moviebooking.payment.integration` package defines lightweight future
contracts for Booking, User Profile, Coupon, and outbound payment-status
notifications. These contracts preserve scalar IDs and do not create external
entities or tables.

They are intentionally unwired: there are no implementations or Spring beans,
and local startup does not depend on unavailable team services. Consequently,
authoritative booking ownership and amount validation, user validation, coupon
calculation/consumption, and downstream status delivery remain integration work
for the Booking, User Profile/authentication, and Coupon teams. Movie, Event,
Hosted Event, Rating, and Feedback should consume payment state through Booking
or the outbound status contract, not through Payment JPA relationships.

## Timestamp strategy

Application entity lifecycle callbacks populate UTC timestamps and keep the H2
profile compatible. PostgreSQL defaults remain as protection for direct
inserts, and the existing PostgreSQL `updated_at` trigger remains enabled for
direct updates. API responses use the timestamps on the persisted entities.

## Compile and package

From the repository root on Windows, run:

```powershell
.\mvnw.cmd -DskipTests clean package
```

This milestone verifies application compilation only. It does not claim that a
live PostgreSQL database or real payment/refund provider was exercised.

## Deferred milestones

Swagger/OpenAPI annotations and dependencies, Postman collections, and new or
rewritten automated tests are deliberately deferred. The next documentation
and testing milestones must cover request and response schemas, enums, headers,
status codes, validation failures, idempotency, transitions, concurrency,
Flyway/PostgreSQL behavior, and external adapter contracts.
