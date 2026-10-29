# Payment_Entity

## Logging

Spring Boot's SLF4J/Logback logging writes to the console and
`logs/movie-booking-system.log` relative to the application's working directory.
Files rotate at 10 MB, with 14 days of history and a 200 MB archive cap.
Set `LOG_FILE` to change the file location and `APP_LOG_LEVEL` to change the
application log level (default `INFO`). Log files are ignored by Git.

Each HTTP response includes a generated `X-Request-ID`. The same ID appears in
request and application logs, linking payment/refund activity to the request.
Request summaries include method, matched route template, response status, and
duration; 4xx responses use WARN and 5xx responses use ERROR. Unexpected server
errors include a stack trace. Payment creation is logged after transaction commit;
payment attempts, refund outcomes, and idempotent replays include internal IDs.

Request/response bodies, query strings, headers, idempotency keys, refund reasons,
and gateway references are not included in the new activity/request logs. SQL
console output is disabled by default. Unexpected exception stack traces can
contain diagnostic details; restrict access to log files.
