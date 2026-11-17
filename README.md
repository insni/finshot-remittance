# Finshot remittance API

KRW → PHP transfer API, Part 1 and Part 2.

## Run

```
docker compose up -d --build app
```

The app listens on `http://localhost:8080`. The database is H2 in-memory; every start has customers C001, C002, C003 from `schema.sql` and no transfers.

Alternatives: `podman-compose up -d --build app` (Podman), or `./mvnw spring-boot:run` (JDK 17+, no container). RESULT.md has the step-by-step guide and the scenario scripts.

Tested on Fedora Linux Asahi Remix 44 (aarch64), Podman 5.8.9 with podman-compose 1.6.0; the app image runs JDK 17.0.20 (Eclipse Temurin).

## Stack

Java 17, Spring Boot 3.5 (web, JDBC), `JdbcTemplate` with plain SQL, H2 in-memory, JUnit 5.

## Retrying create safely

- Send an `Idempotency-Key` header with every `POST /api/transfers`. Create one UUID v4 per user action, and send the same key with the same body on every retry.
- Same key, same body: the original transfer comes back with 201. No second transfer is created.
- Same key, different body: 409.
- Missing or blank key: 400. Keys are per customer and at most 64 characters.

```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1e0c1a-2b7d-4c8e-9a3f-5d2b1c0e7a94' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```

## AI tools

- Claude Code (Anthropic) was used for this assignment.
- Claude Opus 5.5 read the brief, proposed the design (the author reviewed it and made the decisions), reviewed outputs, and wrote these documents.
- Claude Sonnet 5.5 generated the skeleton, the code (Part 1 and Part 2), the tests, the scenario scripts and the Docker setup under that design; the author reviewed and verified the results.

## Not implemented

- Recovery of a transfer stuck in `SENDING` after a crash.
- Checking that a reused `eventId` belongs to the same `partnerRef` (see DESIGN Q5).
- Background sending: `/send` holds the HTTP request open until the partner calls finish.
- Items the brief said not to build: authentication, list endpoints, CI, OpenAPI, logging setup, admin API, metrics, health check, UI.

## Assumptions

The brief left these open:

- Sending is a separate call, `POST /api/transfers/{transferId}/send`. The brief does not say what triggers it, and sending on create would make the S4 cancel impossible.
- `partnerRef` is `"P-" + transferId`, stored before the first partner call.
- The partner is called up to 3 times, waiting 100 ms and then 200 ms between attempts.
- `sendAmount` is a string of digits. Leading zeros are dropped for the request hash (`"0500000"` = `"500000"`).
- `recipientName` is stored as sent, not trimmed. A blank name → 400.
- Validation runs before the idempotency check, so a retry with an invalid body gets 400, not 409.
- Idempotency keys never expire.
- A callback with a `status` other than `COMPLETED` → 400.
- `PartnerClient` computes `abs` on a `long`, so `Integer.MIN_VALUE` gives 2147483648 (succeeds). Java's `int` `Math.abs` would keep it negative.
- Other errors use the same body: an unexpected error → 500 `INTERNAL_ERROR`; an unknown path or method → 404 `NOT_FOUND`.
