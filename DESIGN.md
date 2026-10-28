# DESIGN

Part 1 answers only. Q4 and Q5 are skipped because Part 2 was not attempted.

## Q1. Walk through your tables. Why these indexes and constraints? Which constraint prevents which problem?

Two tables. `customers` is given. `transfers` holds one row per transfer, with the computed amounts stored.

| Constraint | Prevents |
|---|---|
| `UNIQUE (customer_id, idempotency_key)` | A retry creating a second transfer. Its index serves the retry lookup. |
| `FOREIGN KEY` to `customers` | A transfer for an unknown customer. |
| `PRIMARY KEY (transfer_id)` | Duplicate ids. Its index serves `GET /api/transfers/{id}`. |
| `CHECK`: currencies, `send_amount >= 10000`, `fee >= 3000`, `total_debit = send_amount + fee`, `status` in 5 states | Bad rows from an app validation bug or hand-written SQL. The app checks first; these are the last line of defense. |
| `NOT NULL` everywhere | Half-filled rows. |

`INDEX (customer_id, created_at)` is unused in Part 1. It is for the Part 2 daily-limit query: one customer, one day.

Transitions are not a DB constraint. Cancel is `UPDATE ... WHERE status = 'REQUESTED'`.

## Q2. How does idempotency work? What identifies a retry, and where is it enforced — application code, database, or somewhere else? What happens if two identical create requests arrive at exactly the same moment?

The client sends an `Idempotency-Key` header (1-64 chars, UUID v4 suggested). No key → 400.

A retry is identified by `(customerId, Idempotency-Key)`. The row also stores `request_hash`: SHA-256 of the five body fields, with `sendAmount` normalized (`"0500000"` = `"500000"`).

`TransferService.create`: validate → find row by (customer, key) → found: same hash returns the stored transfer with 201, different hash returns 409 → not found: INSERT.

It is enforced in the database by `UNIQUE (customer_id, idempotency_key)`. The app only compares hashes.

Two identical requests at the same moment: both find no row and both INSERT. The DB accepts one. The other gets `DuplicateKeyException` (checked by constraint name), re-reads the winner's row, compares the hash, and returns the same transfer. There is no app lock. `TransferConcurrencyTest` fires 16 parallel creates and checks one row and one `transferId`.

A replay returns the transfer's current state, e.g. `CANCELLED` after a cancel.

## Q3. How do you represent, store, and round money in code, API, and database? Where exactly does rounding happen, and why there?

- API: every amount is a JSON string. Clients send `sendAmount` as whole won, digits only. Responses: `"fee": "5000"`, `"receiveAmount": "20600.00"` (always 2 decimals). Strings keep clients from parsing money as floats.
- Code: `BigDecimal` only, never `double`. `toPlainString()` avoids exponent form.
- DB: KRW (`send_amount`, `fee`, `total_debit`) is `BIGINT` whole won, because KRW has no minor unit. PHP `receive_amount` is `DECIMAL(15,2)`.

Rounding happens in one method, `TransferCalculator.calculate`, called once when the transfer is created:

- fee = sendAmount × 0.01, `setScale(0, HALF_UP)`, then at least 3000
- receiveAmount = sendAmount × 0.0412, `setScale(2, HALF_UP)`
- totalDebit = sendAmount + fee, no rounding needed

The results are stored. GET, cancel and replay read the stored values and never recompute.

Why there: one rule lives in one class, rounding once means no double rounding, and a stored amount does not change if the rate or rule changes later.
