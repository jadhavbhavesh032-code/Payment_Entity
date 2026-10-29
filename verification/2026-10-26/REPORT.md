# Payment Entity verification - 26 October 2026

Work was performed in the existing `Payment_Entity/movie-booking-system` directory.
No replacement project or ZIP was created. Original sources, pom.xml and HELP.md
were copied to `Payment_Entity/backups/2026-10-26-before-postgres-openapi`.
Existing H2 files, Java business logic, entity mappings, migrations, and other
workspace files were preserved.

## Database inventory and reset

Initial inventory:

| Instance | Databases |
| --- | --- |
| PostgreSQL 18.4, localhost:5432 | movie_booking_db, postgres, template0, template1 |
| PostgreSQL 17, localhost:5433 | postgres, template0, template1 |

`movie_booking_db` was the only existing project database. Its only application
tables were `payments.payment`, `payments.payment_transaction`, and
`payments.refund`, each containing zero rows. Flyway V1 was installed.
There were no other connected sessions. The exact deletion target was shown
in chat; the user then instructed deletion of the existing databases for the
project. Only `localhost:5432/movie_booking_db` was deleted.

Backup: `Payment_Entity/backups/2026-10-26-before-postgres-openapi/movie_booking_db-pg18-port5432.dump`.

SHA-256: `386C0DC0211AB593389A48BDF05C9C9C6B4F93D6D32293A4A2B7A34446C4228F`.

The custom-format pg_dump archive was listed with pg_restore, then successfully
restored using `--exit-on-error` into `payment_entity_restore_check`. V1 and the
empty payment table were queried after restoration. That temporary restore
database was removed after verification.

Final databases on port 5432: `payment_entity_db` (application),
`payment_entity_test` (separate integration tests), `postgres`, `template0`,
`template1`. Port 5433 was unchanged. No system or unrelated database was deleted.

Both fresh databases received the existing V1 and V2 migrations, and Hibernate
validated the mappings. V2 enforces idempotency uniqueness and the composite
refund/payment/transaction relationship. Existing migration files were not edited.
Booking/user/coupon references remain logical references pending the existing
external-module integrations.

## Changes

- PostgreSQL is the default application profile, targeting `payment_entity_db`.
- The optional H2 `local` profile remains available explicitly.
- Added springdoc-openapi-starter-webmvc-ui 3.2.0, compatible with Boot 4.1.x:
  https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot.
- Added API title/description, request examples, required Idempotency-Key examples
  and semantics, and accurate POST response descriptions for 201/200/400/404/409.
- Added HTTP integration tests, an opt-in PostgreSQL test profile, a 34-request
  Postman collection and its generator, restart script, and updated HELP.md.
- No payment/refund behavior defect was observed in the exercised flows; no
  business-layer or entity rewrite was needed.

## Verification results

| Check | Result |
| --- | --- |
| Maven H2 suite: `.\mvnw.cmd -q test` | 3 tests, 0 failures/errors/skips |
| PostgreSQL suite: `.\mvnw.cmd -q '-Dspring.profiles.active=postgres-test' test` | 3 tests, 0 failures/errors/skips |
| Packaged application: `.\mvnw.cmd -q -DskipTests package` | Passed |
| Final Postman collection run through Newman 6 | 34 requests, 45 assertions, 0 failures |
| Application Flyway first startup | V1 and V2 applied successfully |
| Final packaged application startup without profile argument | Defaults to postgres; V2 validates and remains current |
| Actuator `/actuator/health` | UP |
| Swagger UI and `/v3/api-docs` | HTTP 200; 11 API operations documented |
| Swagger browser Try it out | Payment creation 201; UPI processing 201/SUCCESS; identical retry 200 with same transaction |
| PowerShell restart script | Syntax parsed successfully |

Collection coverage includes valid payment creation, duplicate bookings, invalid
IDs/amounts, unknown fields, invalid pagination/sort/enums, missing/blank/long keys,
refund-before-payment rejection, payment success, identical and conflicting
payment retries, second-charge prevention, partial refund, refund replay/conflict,
over-refund rejection, remaining-balance refund, fully refunded state, rejection
of additional refunds, history cardinality, individual reads, booking lookup,
cancellation conflict, missing payments, and OpenAPI required headers/examples.

Evidence is stored beside this report: `h2/`, `postgres/`,
`postman-results.json`, `database-verification.txt`, and `swagger-replay.png`.
The application database contains retained mock test records from the collection
and Swagger verification; the PostgreSQL test suite uses the separate test database.

## Running again

From the existing project directory run `.\scripts\Start-Payment.ps1`.
It prompts for the database password when DB_PASSWORD is unset; credentials are
kept in process environment variables and are not written to project files.
The application was left running at http://localhost:9080 using PostgreSQL.
Swagger: http://localhost:9080/swagger-ui/index.html.

Import `postman/PaymentEntity.postman_collection.json` into Postman to run it in
the desktop UI, or repeat the verified runner command:

```powershell
npx.cmd --yes newman@6 run postman/PaymentEntity.postman_collection.json --reporters cli,json --reporter-json-export verification/2026-10-26/postman-results.json
```

## Limits and remaining items

Postman desktop was not found in the installed-app inventory or the standard
local installation path. The collection was executed using Postman's Newman
runner; a Postman desktop UI run remains unverified. To perform that exact UI
step, install/open Postman and import the saved collection. No further database
credentials are currently needed.

The existing payment and refund gateways are mocks and always approve valid
requests. No real money moved. Real gateway connectivity, upstream booking/user/
coupon integrations, and production authentication remain outside this local
verification. This run does not establish those capabilities.
