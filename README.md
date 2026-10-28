# Finshot remittance API (Part 1)

KRW → PHP transfer API: create, read and cancel a transfer. Part 1 only.

## Run

```
./mvnw spring-boot:run
```

Needs JDK 17 or newer, nothing else (checked with JDK 25). The Maven wrapper downloads Maven and the dependencies on the first run.
The app listens on `http://localhost:8080`. The database is H2 in-memory, so every start is empty except customers C001, C002, C003 from `schema.sql`.

Scenarios S1-S6, against a freshly started app, in a second terminal:

```
./scripts/scenarios.sh        # BASE defaults to http://localhost:8080
```

Tests: `./mvnw test` (saved output: `test-output.txt`).

## Run with Docker (optional, no JDK needed)

Only Docker with Compose is needed.

```
docker compose up -d app              # build and start on http://localhost:8080
docker compose run --rm scenarios     # S1-S6 against the running app
docker compose run --rm test          # unit and integration tests
docker compose down                   # stop and remove
```

Run the scenarios against a freshly started app, as above. With `podman-compose`, add `--profile tools` to the `run` commands.

## Stack

Java 17 (source level), Spring Boot 3.5 (web, JDBC), `JdbcTemplate` with plain SQL (no JPA), H2 in-memory, JUnit 5.

## Retrying create safely

Send an `Idempotency-Key` header with every `POST /api/transfers`. Generate the value once per user action (a UUID v4 when the user presses "Send"), keep it, and send the same key with the same body on every retry (timeout, lost connection, double click). The server then returns the original transfer with 201 and does not create a second one. A missing or blank key → 400. The same key with a different body → 409. Keys are scoped per customer, so C001 and C002 can use the same key without conflict. A key is at most 64 characters.

```
curl -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1e0c1a-2b7d-4c8e-9a3f-5d2b1c0e7a94' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```

## AI tools

- Claude Code (Anthropic) was used for this assignment.
- Claude Opus 5.5 read the brief, proposed the design (the author reviewed it and made the decisions), reviewed outputs, and wrote these documents.
- Claude Sonnet 5.5 generated the skeleton, the code, the tests and the scenario script under that design; the author reviewed and verified the results.

## Assumptions

The brief left these open. Each one is what the code does.

- `sendAmount` should be a JSON string of digits. A JSON integer such as `500000` is also accepted, because Jackson turns it into text. A decimal or negative value fails the digit check → 400.
- Leading zeros are allowed and normalized. `"0500000"` is stored as 500000 and counts as the same request as `"500000"` on retry.
- Upper limit: `sendAmount` above 1,000,000,000,000 KRW → 400. This keeps every amount inside the `BIGINT` and `DECIMAL(15,2)` columns.
- Only `sendAmount` is normalized for the request hash. Other fields are compared exactly, so `"Juan Dela Cruz "` (trailing space) with the same key → 409. `recipientName` is stored as sent, not trimmed; a name of only spaces counts as empty → 400.
- Currency values are case-sensitive: `"krw"` → 400.
- Validation runs before the idempotency check. A retry with the same key but an invalid body gets 400, not 409.
- A retry returns 201 with the transfer's current state, not a saved copy of the first response. After a cancel it shows `CANCELLED`.
- Unknown body fields are ignored and are not part of the request hash.
- The key format is not checked. Any non-blank value up to 64 characters works (the scenario script uses `K1`, `K2`, `K3`).
- Keys never expire.
- Errors: an unexpected server error → 500 with `"error": "INTERNAL_ERROR"`, a code the brief does not define. Broken JSON or a non-JSON `Content-Type` → 400 `VALIDATION_ERROR`. An unknown route or a wrong HTTP method (405) → 404 `NOT_FOUND`.
- Responses have extra fields: `customerId`, `sendCurrency`, `receiveCurrency`, `recipientName`. `createdAt` is ISO-8601 in UTC, e.g. `2026-10-28T11:32:01.813Z`.
- Cancel returns 200 with the full transfer.

## Not implemented

- All of Part 2: `PartnerClient` and its retries, moving a transfer to `SENDING` / `COMPLETED` / `FAILED` (the states and the transition table exist, but only cancel changes a status), the daily limit (422 `LIMIT_EXCEEDED`), the partner callback endpoint, scenarios S7-S9, and DESIGN Q4-Q5.
- Things the brief says not to build: authentication and user accounts, list/paging/filter endpoints, Docker, CI, OpenAPI/Swagger, logging framework setup, admin API, metrics, health check, UI.
