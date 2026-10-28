# Finshot remittance API

KRW → PHP transfer API: create, read and cancel a transfer (Part 1), and the optional Part 2: send to a fake partner with retries, a daily limit, and the partner callback.

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

Scenarios S7-S9 (Part 2), against a freshly started app (the C002 daily limit stays used until restart):

```
./scripts/scenarios-part2.sh
```

Tests: `./mvnw test` (saved output: `test-output.txt`).

## Run with Docker (optional, no JDK needed)

Only Docker with Compose is needed.

```
docker compose up -d app              # build and start on http://localhost:8080
docker compose run --rm scenarios     # S1-S6 against the running app
docker compose run --rm -e SCRIPT=scenarios-part2.sh scenarios   # S7-S9 (restart the app first for a clean C002 limit)
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

## Part 2: send, limit, callback

- `POST /api/transfers/{transferId}/send` starts the partner call. The brief does not say what triggers it, and sending automatically on create would make the Part 1 cancel (S4) impossible, so sending is an explicit step. Only from `REQUESTED`, otherwise 409 (unknown id → 404). It runs synchronously and returns 200 with the transfer: `SENDING` if the partner accepted, `FAILED` (with `failureReason`) if all 3 attempts failed. The body also has `partnerRef` and an `attempts` list (attemptNo, result, attemptedAt) so the retry log is visible in the response; the same rows are in table `partner_attempts` and in the app log.
- `POST /api/callbacks/partner` with `{"partnerRef":"P-<transferId>","eventId":"evt_0001","status":"COMPLETED"}` moves `SENDING` → `COMPLETED` and returns 200 with the transfer. Same `eventId` again → 200 with the current transfer, nothing changes. Unknown `partnerRef` → 404. Transfer not in `SENDING` → 409. `status` other than `COMPLETED` or a blank field → 400.

Assumptions:

- `partnerRef` is `"P-" + transferId`, fixed per transfer and stored before the first partner call, so a callback can always find it. Retries do not change it (the partner never sees a duplicate), so a ref that fails the `hashCode` rule fails all 3 attempts; about 30% of transfers end `FAILED`.
- 3 attempts in total, waiting 100 ms and then 200 ms between them. Each attempt takes at least 200 ms. The partner is called with no DB transaction open.
- A transfer ends in `SENDING` after the partner accepts, never `COMPLETED` by the send itself. If a callback arrives while the retries are still running, the late `FAILED` update does not overwrite `COMPLETED`.
- Daily limit: 3,000,000 KRW of `sendAmount` per customer per Asia/Seoul calendar day (by the transfer's `createdAt`). `REQUESTED`, `SENDING` and `COMPLETED` count; `CANCELLED` and `FAILED` do not. A transfer that would take the total above the limit → 422 `LIMIT_EXCEEDED`; exactly 3,000,000 is allowed. Creates for the same customer are serialized by a row lock, so two concurrent requests cannot both pass.
- A retry of an existing `Idempotency-Key` returns the original transfer and skips the limit check.
- No recovery job: if the app dies between `SENDING` and the partner result, the transfer stays `SENDING`.

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

- DESIGN Q4-Q5 and the S7-S9 results in RESULT.md are not written yet (the code and `scripts/scenarios-part2.sh` are there).
- Recovery of a transfer stuck in `SENDING` after a crash, and a callback-driven `FAILED`/`CANCELLED` (the brief says the partner only sends `COMPLETED`).
- Things the brief says not to build: authentication and user accounts, list/paging/filter endpoints, CI, OpenAPI/Swagger, logging framework setup, admin API, metrics, health check, UI.
