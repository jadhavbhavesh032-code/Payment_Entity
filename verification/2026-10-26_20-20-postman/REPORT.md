# Payment Entity - Postman Test Report

**Overall result: PASS**

Run started: 26 October 2026, 20:20:50 IST. Duration: 4.42 seconds.

Target: `http://localhost:9080`, existing Payment Entity application, Spring Boot 4.1.2, PostgreSQL database `payment_entity_db` on localhost:5432.

- Requests: **34**, failed: **0**.
- Assertions: **45**, failed: **0**.
- Recorded Newman failures: **0**.

## Results for every request

Expected 400, 404 and 409 responses are successful negative tests, not test failures.

| # | Test | Expected HTTP | Actual HTTP | Assertions passed | Response ms | Result |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Create payment | 201 | 201 | 2/2 | 297 | PASS |
| 2 | Duplicate booking | 409 | 409 | 1/1 | 15 | PASS |
| 3 | Invalid negative amount and IDs | 400 | 400 | 1/1 | 12 | PASS |
| 4 | Unknown JSON property | 400 | 400 | 1/1 | 9 | PASS |
| 5 | Invalid pagination | 400 | 400 | 1/1 | 16 | PASS |
| 6 | Invalid sort | 400 | 400 | 1/1 | 5 | PASS |
| 7 | Invalid enum | 400 | 400 | 1/1 | 17 | PASS |
| 8 | Missing idempotency header | 400 | 400 | 1/1 | 6 | PASS |
| 9 | Blank idempotency header | 400 | 400 | 1/1 | 10 | PASS |
| 10 | Oversized idempotency header | 400 | 400 | 1/1 | 7 | PASS |
| 11 | Refund before payment | 409 | 409 | 1/1 | 92 | PASS |
| 12 | Process payment | 201 | 201 | 2/2 | 178 | PASS |
| 13 | Replay payment | 200 | 200 | 2/2 | 12 | PASS |
| 14 | Conflicting payment replay | 409 | 409 | 1/1 | 12 | PASS |
| 15 | Prevent second charge | 409 | 409 | 1/1 | 15 | PASS |
| 16 | Read paid payment | 200 | 200 | 2/2 | 17 | PASS |
| 17 | Missing refund header | 400 | 400 | 1/1 | 3 | PASS |
| 18 | Invalid refund | 400 | 400 | 1/1 | 6 | PASS |
| 19 | Partial refund | 201 | 201 | 2/2 | 68 | PASS |
| 20 | Replay refund | 200 | 200 | 2/2 | 23 | PASS |
| 21 | Conflicting refund replay | 409 | 409 | 1/1 | 12 | PASS |
| 22 | Over-refund rejected | 409 | 409 | 1/1 | 12 | PASS |
| 23 | Read partially refunded | 200 | 200 | 2/2 | 10 | PASS |
| 24 | Refund remaining balance | 201 | 201 | 1/1 | 34 | PASS |
| 25 | Read fully refunded | 200 | 200 | 2/2 | 8 | PASS |
| 26 | Reject refund after full refund | 409 | 409 | 1/1 | 12 | PASS |
| 27 | Transaction history has one charge | 200 | 200 | 2/2 | 42 | PASS |
| 28 | Refund history has two refunds | 200 | 200 | 2/2 | 19 | PASS |
| 29 | Read transaction | 200 | 200 | 1/1 | 10 | PASS |
| 30 | Read refund | 200 | 200 | 1/1 | 18 | PASS |
| 31 | Read by booking | 200 | 200 | 1/1 | 12 | PASS |
| 32 | Reject cancelling refunded payment | 409 | 409 | 1/1 | 6 | PASS |
| 33 | Missing payment | 404 | 404 | 1/1 | 7 | PASS |
| 34 | OpenAPI required idempotency headers | 200 | 200 | 2/2 | 562 | PASS |

## Test records and database checks

The collection generated new IDs and retained its mock financial records:

```json
{
  "paymentId": 4,
  "bookingId": 1793026250288,
  "transactionId": 4,
  "refundIds": [
    5,
    6
  ]
}
```

The application health was UP before testing. PostgreSQL showed an active application connection pool, and Flyway V1 and V2 were both successful. The final database query is saved in `database-verification.txt` beside this report.

## Coverage

Payment creation and duplicate booking detection; input, enum and pagination validation; required/blank/oversized idempotency keys; payment processing, identical retries and conflicting retries; prevention of a second charge; refund eligibility; partial and full refunds; refund replay and conflict detection; over-refund prevention; final financial state; history cardinality; individual and booking reads; cancellation conflicts; missing payments; OpenAPI required headers and request examples.

## Exact requests executed

1. **Create payment**: `POST http://localhost:9080/api/payments`
2. **Duplicate booking**: `POST http://localhost:9080/api/payments`
3. **Invalid negative amount and IDs**: `POST http://localhost:9080/api/payments`
4. **Unknown JSON property**: `POST http://localhost:9080/api/payments`
5. **Invalid pagination**: `GET http://localhost:9080/api/payments?size=101`
6. **Invalid sort**: `GET http://localhost:9080/api/payments?sort=unknown,desc`
7. **Invalid enum**: `POST http://localhost:9080/api/payments/4/transactions`
8. **Missing idempotency header**: `POST http://localhost:9080/api/payments/4/transactions`
9. **Blank idempotency header**: `POST http://localhost:9080/api/payments/4/transactions`
10. **Oversized idempotency header**: `POST http://localhost:9080/api/payments/4/transactions`
11. **Refund before payment**: `POST http://localhost:9080/api/payments/4/refunds`
12. **Process payment**: `POST http://localhost:9080/api/payments/4/transactions`
13. **Replay payment**: `POST http://localhost:9080/api/payments/4/transactions`
14. **Conflicting payment replay**: `POST http://localhost:9080/api/payments/4/transactions`
15. **Prevent second charge**: `POST http://localhost:9080/api/payments/4/transactions`
16. **Read paid payment**: `GET http://localhost:9080/api/payments/4`
17. **Missing refund header**: `POST http://localhost:9080/api/payments/4/refunds`
18. **Invalid refund**: `POST http://localhost:9080/api/payments/4/refunds`
19. **Partial refund**: `POST http://localhost:9080/api/payments/4/refunds`
20. **Replay refund**: `POST http://localhost:9080/api/payments/4/refunds`
21. **Conflicting refund replay**: `POST http://localhost:9080/api/payments/4/refunds`
22. **Over-refund rejected**: `POST http://localhost:9080/api/payments/4/refunds`
23. **Read partially refunded**: `GET http://localhost:9080/api/payments/4`
24. **Refund remaining balance**: `POST http://localhost:9080/api/payments/4/refunds`
25. **Read fully refunded**: `GET http://localhost:9080/api/payments/4`
26. **Reject refund after full refund**: `POST http://localhost:9080/api/payments/4/refunds`
27. **Transaction history has one charge**: `GET http://localhost:9080/api/payments/4/transactions`
28. **Refund history has two refunds**: `GET http://localhost:9080/api/payments/4/refunds`
29. **Read transaction**: `GET http://localhost:9080/api/payments/4/transactions/4`
30. **Read refund**: `GET http://localhost:9080/api/payments/4/refunds/5`
31. **Read by booking**: `GET http://localhost:9080/api/payments/booking/1793026250288`
32. **Reject cancelling refunded payment**: `POST http://localhost:9080/api/payments/4/cancel`
33. **Missing payment**: `GET http://localhost:9080/api/payments/9223372036854775807`
34. **OpenAPI required idempotency headers**: `GET http://localhost:9080/v3/api-docs`

## Failures and limitations

No request, assertion or script failures were recorded. No application-code fix was needed during this run.

Tests were executed using Postman's Newman 6 runner against the running application. This was not a manual Postman desktop UI run. The existing payment/refund gateways are mocks; no real money moved. Live gateway and external-module integrations were not tested. This run executed the 34-request collection; it did not rerun the Maven test suite.

## Evidence and reproduction

- `newman-results.json`: complete machine-readable request/response and assertion results.
- `newman-results.xml`: JUnit-format result export.
- `newman-console.txt`: runner output.
- `database-verification.txt`: persisted state, migrations and final health.

Run from the existing project directory:

```powershell
npx.cmd --yes newman@6 run postman/PaymentEntity.postman_collection.json
```
