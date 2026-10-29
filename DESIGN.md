# DESIGN

Answers to Q1-Q5 (Part 1 and Part 2).

## Q1. Walk through your tables. Why these indexes and constraints? Which constraint prevents which problem?

Four tables. `customers` is given. `transfers` holds one row per transfer, with the computed amounts stored. Part 2 adds `partner_attempts` and `callback_events`.

| Constraint | Prevents |
|---|---|
| `UNIQUE (customer_id, idempotency_key)` | A retry creating a second transfer. Its index serves the retry lookup. |
| `FOREIGN KEY` to `customers` | A transfer for an unknown customer. |
| `PRIMARY KEY (transfer_id)` | Duplicate ids. Its index serves `GET /api/transfers/{id}`. |
| `UNIQUE (partner_ref)` | Two transfers with one ref. Its index serves the callback lookup. |
| `PRIMARY KEY (event_id)` | One callback applied twice. |
| `CHECK`: currencies, `send_amount >= 10000`, `fee >= 3000`, `total_debit = send_amount + fee`, `status` in 5 states | Bad rows from an app validation bug or hand-written SQL. The app checks first; these are the last line of defense. |
| `NOT NULL` except `partner_ref`, `failure_reason` | Half-filled rows. |

`INDEX (customer_id, created_at)` serves the daily-limit sum: one customer, one day.

Transitions are conditional UPDATEs, not DB constraints, e.g. cancel is `UPDATE ... WHERE status = 'REQUESTED'`.

## Q2. How does idempotency work? What identifies a retry, and where is it enforced — application code, database, or somewhere else? What happens if two identical create requests arrive at exactly the same moment?

The client sends an `Idempotency-Key` header (1-64 chars, UUID v4 suggested). No key → 400.

A retry is identified by `(customerId, Idempotency-Key)`. The row also stores `request_hash`: SHA-256 of the five body fields, with `sendAmount` normalized (`"0500000"` = `"500000"`).

`TransferService.create`: validate → find row by (customer, key) → found: same hash returns the stored transfer with 201, different hash returns 409 → not found: INSERT.

It is enforced in the database by `UNIQUE (customer_id, idempotency_key)`. The app only compares hashes.

Two identical requests at the same moment: both find no row. The insert transaction locks the customer row first (Q4): one inserts, the other waits, re-reads by key, and returns the winner's transfer (hash compared). The unique constraint is the backstop: a duplicate INSERT → `DuplicateKeyException` (checked by constraint name) → same replay. `S2RetryTest`: 16 parallel creates, one row, one `transferId`.

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

## Q4. How did you make the daily limit safe when requests arrive at the same time? Name the mechanism (row lock, unique constraint, isolation level, other) and say what you gave up with that choice.

A pessimistic row lock. If the key is new, `TransferService.create` runs one transaction:

1. `SELECT ... FROM customers WHERE customer_id = ? FOR UPDATE`
2. Re-read the `(customer, Idempotency-Key)` row: a same-key request may have committed while we waited; if so, replay it (no limit check).
3. Sum today's `sendAmount` (Asia/Seoul day, `CANCELLED` and `FAILED` excluded).
4. INSERT, or 422 if the sum would pass 3,000,000.

A second create for the same customer waits at step 1, so its sum includes the first insert. Other customers are not blocked.

`S7DailyLimitTest`: 8 parallel creates of 1,000,000 → exactly 3 pass. With the lock line removed, 8 pass.

Given up:
- Creates for one customer run one at a time, holding the lock until commit.
- It needs DB row locks and READ COMMITTED (H2's default) so the sum sees the previous insert.
- No per-day counter row or unique key; the sum is recomputed every time.

## Q5. The partner callback arrives before your code saves the partner call result. What happens in your code? If it breaks, say so and explain how you would fix it.

That order is safe. `send` commits `SENDING` and `partner_ref` in one conditional UPDATE before the first partner call, so the callback finds it and moves `SENDING` → `COMPLETED`. We then only add the attempt row. `fail()` runs `WHERE status = 'SENDING'`, so it cannot overwrite `COMPLETED`. Before that commit, `partner_ref` is NULL (404), but the partner cannot know it yet.

My implementation breaks if the app dies, or `send` throws, after the `SENDING` commit:
- Partner never accepted: the transfer stays `SENDING` forever, still counts toward the limit, and `/send` returns 409. Fix: a scheduled job asks the partner about old `SENDING` transfers by `partnerRef`, then retries or marks `FAILED`.
- Partner accepted: the callback still completes it, but the attempt row is lost. Fix: write the attempt before the call, its result after.

Also: a reused `eventId` for another `partnerRef` returns 200 and changes nothing. Fix: compare with the stored event; mismatch → 409.
