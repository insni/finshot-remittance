# RESULT

S1-S6 were run in order against a freshly started app on 2026-10-28.
S7-S9 (Part 2) were run later the same day against another freshly started app, see [Part 2 scenarios](#part-2-scenarios-s7-s9).
H2 is in-memory, so a fresh start has no transfers.

How it was run:

```
./mvnw -q -DskipTests package
java -jar target/remittance-0.0.1-SNAPSHOT.jar
BASE=http://localhost:8080 ./scripts/scenarios.sh    # while the app is running
```

The blocks below are the script's real output. Only the layout was changed to match the brief
(the status line was moved and `json` was added to the response fence). No request or response value was edited.

Notes:

- Headers: the script sets only `Content-Type` and `Idempotency-Key`, shown in each request.
  curl also sends its default `Host`, `User-Agent` and `Accept` headers. The server does not use them.
- The keys `K1`, `K2`, `K3` are short so the output is easy to read. A real client should send a UUID v4 (see README).
- The cancel requests have no body and no extra headers.

## Summary

| # | Expected | Got |
|---|---|---|
| S1 | 201, new transfer | 201, `transferId` 9e55062d-... |
| S2 | original transfer, no second one | 201, same `transferId` and `createdAt` as S1 |
| S3 | 409 | 409 CONFLICT |
| S4 | create, then cancel | 201, then 200 with `status` CANCELLED |
| S5 | 409 | 409 CONFLICT |
| S6 | 400 | 400 VALIDATION_ERROR |
| S7 | creates pass up to the limit, then 422 | 201, 201, 201 (total 3,000,000), then 422 LIMIT_EXCEEDED |
| S8 | same callback twice: both succeed, state changes once | 200 COMPLETED, 200 COMPLETED (no change), GET COMPLETED |
| S9 | 3 failed attempts, then FAILED | send 200 FAILED with 3 FAILED attempts, GET FAILED |

All nine scenarios behaved as expected.

## Reproduce with curl

`scripts/scenarios.sh` makes the same calls. Example for S1 and S2 (run the same command twice):

```
curl -s -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K1' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```

Cancel (replace the id with the `transferId` from the create response):

```
curl -s -X POST http://localhost:8080/api/transfers/<transferId>/cancel
```

## Scenarios

### S1

Customer C001 creates a transfer of 500000 KRW.

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: K1
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"9e55062d-16a0-4a09-afdc-a172eea05a0d","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-28T11:32:01.813Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S2

Retry of S1: same key `K1`, exactly the same body.

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: K1
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"9e55062d-16a0-4a09-afdc-a172eea05a0d","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-28T11:32:01.813Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S3

Same key `K1` as S1, but sendAmount changed to 600000.

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: K1
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"600000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 409
Response:
```json
{"error":"CONFLICT","message":"Idempotency-Key was already used with a different request"}
```

### S4

Create another transfer (key `K2`), then cancel it.

**S4a: create**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: K2
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"57cc09be-4f67-4be7-8cf5-b214067425f6","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-28T11:32:01.883Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S4b: cancel**

Request:
```
POST /api/transfers/57cc09be-4f67-4be7-8cf5-b214067425f6/cancel
```
Status: 200
Response:
```json
{"transferId":"57cc09be-4f67-4be7-8cf5-b214067425f6","status":"CANCELLED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-28T11:32:01.883Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S5

Cancel the S4 transfer again.

Request:
```
POST /api/transfers/57cc09be-4f67-4be7-8cf5-b214067425f6/cancel
```
Status: 409
Response:
```json
{"error":"CONFLICT","message":"cannot cancel a transfer in status CANCELLED"}
```

### S6

Create with sendAmount 5000.

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: K3
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"5000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 400
Response:
```json
{"error":"VALIDATION_ERROR","message":"sendAmount must be at least 10000"}
```

## Part 2 scenarios (S7-S9)

S7-S9 were run in order against another freshly started app on 2026-10-28, so C002 had no transfers yet.

How it was run:

```
./mvnw -q -DskipTests package
java -jar target/remittance-0.0.1-SNAPSHOT.jar > app.log     # app log kept for the S9 retry lines
BASE=http://localhost:8080 ./scripts/scenarios-part2.sh      # while the app is running
```

Same rules as for S1-S6: the blocks are the script's real output, only the layout was changed.

Notes:

- Create requests set `Content-Type` and `Idempotency-Key`. Callback requests set only `Content-Type`. Send and GET requests have no body and no extra headers.
- The script puts a run number in every key (`s7-1793188900-1`), so the keys do not clash if it runs twice on one app. The C002 daily limit still stays used until a restart.
- The partner result depends only on `partnerRef` (`"P-" + transferId`, and the id is a random UUID), and about 30% of refs fail. So S8 and S9 loop: create a 100,000 KRW transfer (C001 for S8, C003 for S9) and send it, until the send ends in `SENDING` (S8) or `FAILED` (S9), at most 50 tries. **This run: S8 needed 2 tries, S9 needed 7 tries.** The earlier tries are listed in a table (their requests differ only in the key). The try that counts is shown in full.
- The S9 tries 1-6 stay `SENDING`: the script sends no callback for them.

### S7

C002 creates transfers of 1,000,000 KRW until the daily limit (3,000,000 KRW per Asia/Seoul day) is passed. The third create makes the total exactly 3,000,000, which is allowed, so it is the last success. The fourth is rejected.

**S7-1: create**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-1793188900-1
{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"85e28ccf-32d3-432c-bca0-142da16b8f76","status":"REQUESTED","sendAmount":"1000000","fee":"10000","totalDebit":"1010000","receiveAmount":"41200.00","createdAt":"2026-10-28T12:01:40.343Z","customerId":"C002","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S7-2: create**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-1793188900-2
{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"ecfb2417-5ad5-4db6-95be-c424c258634b","status":"REQUESTED","sendAmount":"1000000","fee":"10000","totalDebit":"1010000","receiveAmount":"41200.00","createdAt":"2026-10-28T12:01:40.376Z","customerId":"C002","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S7-3: last success (total 3,000,000)**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-1793188900-3
{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"ffa7cc73-d32f-40be-964f-aca40f838ddb","status":"REQUESTED","sendAmount":"1000000","fee":"10000","totalDebit":"1010000","receiveAmount":"41200.00","createdAt":"2026-10-28T12:01:40.388Z","customerId":"C002","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S7-4: rejected**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s7-1793188900-4
{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 422
Response:
```json
{"error":"LIMIT_EXCEEDED","message":"daily limit of 3000000 KRW exceeded: already 3000000 today (Asia/Seoul)"}
```

### S8

The same partner callback (same `eventId`) is sent twice to a transfer in `SENDING`.

Tries until a send ended in `SENDING`:

| Try | transferId | send result | attempts |
|---|---|---|---|
| 1 | `9ae0b98a-9eb7-41b4-976b-968c4459066d` | FAILED | FAILED, FAILED, FAILED |
| 2 | `83226e46-5b75-489b-88f4-66e306a8619c` | SENDING | ACCEPTED |

**S8 try 2: create**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s8-1793188900-2
{"customerId":"C001","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"83226e46-5b75-489b-88f4-66e306a8619c","status":"REQUESTED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:41.411Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S8 try 2: send** (the partner accepted, so the transfer stays `SENDING` and waits for the callback)

Request:
```
POST /api/transfers/83226e46-5b75-489b-88f4-66e306a8619c/send
```
Status: 200
Response:
```json
{"transferId":"83226e46-5b75-489b-88f4-66e306a8619c","status":"SENDING","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:41.411Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c","attempts":[{"attemptNo":1,"result":"ACCEPTED","attemptedAt":"2026-10-28T12:01:41.637Z"}]}
```

**S8a: callback, first delivery** → `SENDING` to `COMPLETED`

Request:
```
POST /api/callbacks/partner
Content-Type: application/json
{"partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c","eventId":"evt_1793188900","status":"COMPLETED"}
```
Status: 200
Response:
```json
{"transferId":"83226e46-5b75-489b-88f4-66e306a8619c","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:41.411Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c"}
```

**S8b: same callback again** (same `eventId`) → 200, no error, nothing changes

Request:
```
POST /api/callbacks/partner
Content-Type: application/json
{"partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c","eventId":"evt_1793188900","status":"COMPLETED"}
```
Status: 200
Response:
```json
{"transferId":"83226e46-5b75-489b-88f4-66e306a8619c","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:41.411Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c"}
```

**S8c: final state**

Request:
```
GET /api/transfers/83226e46-5b75-489b-88f4-66e306a8619c
```
Status: 200
Response:
```json
{"transferId":"83226e46-5b75-489b-88f4-66e306a8619c","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:41.411Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-83226e46-5b75-489b-88f4-66e306a8619c"}
```

### S9

A transfer whose `partnerRef` makes the partner fail. All 3 attempts fail and the transfer ends `FAILED`.

Tries until a send ended in `FAILED`:

| Try | transferId | send result | attempts |
|---|---|---|---|
| 1 | `75d8d179-f06d-4c9b-a4b1-e3919ecc7f67` | SENDING | ACCEPTED |
| 2 | `abc87095-a710-4d0a-94c9-2e688cbb44b8` | SENDING | ACCEPTED |
| 3 | `6c5afc3e-b614-4f58-a710-987abb42c76d` | SENDING | ACCEPTED |
| 4 | `2593ec36-fe1e-40fd-ac40-99a0fa7ce323` | SENDING | ACCEPTED |
| 5 | `529ecbd1-bc08-4ec8-8759-08a407596b4c` | SENDING | ACCEPTED |
| 6 | `a464fd46-5f51-4a7a-889d-6c5d974b9f4f` | SENDING | ACCEPTED |
| 7 | `68c1a73c-b262-4d31-8787-3e7fc462e935` | FAILED | FAILED, FAILED, FAILED |

**S9 try 7: create**

Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: s9-1793188900-7
{"customerId":"C003","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```
Status: 201
Response:
```json
{"transferId":"68c1a73c-b262-4d31-8787-3e7fc462e935","status":"REQUESTED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:43.183Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

**S9 try 7: send**

Request:
```
POST /api/transfers/68c1a73c-b262-4d31-8787-3e7fc462e935/send
```
Status: 200
Response:
```json
{"transferId":"68c1a73c-b262-4d31-8787-3e7fc462e935","status":"FAILED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:43.183Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-68c1a73c-b262-4d31-8787-3e7fc462e935","failureReason":"partner rejected 3 attempts","attempts":[{"attemptNo":1,"result":"FAILED","attemptedAt":"2026-10-28T12:01:43.403Z"},{"attemptNo":2,"result":"FAILED","attemptedAt":"2026-10-28T12:01:43.705Z"},{"attemptNo":3,"result":"FAILED","attemptedAt":"2026-10-28T12:01:44.108Z"}]}
```

**Retry log.** The send response above has the `attempts` array:

```json
[{"attemptNo":1,"result":"FAILED","attemptedAt":"2026-10-28T12:01:43.403Z"},{"attemptNo":2,"result":"FAILED","attemptedAt":"2026-10-28T12:01:43.705Z"},{"attemptNo":3,"result":"FAILED","attemptedAt":"2026-10-28T12:01:44.108Z"}]
```

The attempts are 302 ms and 403 ms apart: 200 ms per partner call plus the 100 ms and 200 ms backoff. The same rows are in table `partner_attempts`. The matching app log lines for `P-68c1a73c-b262-4d31-8787-3e7fc462e935` (the log shows local time, +09:00; `attemptedAt` is UTC):

```
2026-10-28T21:01:43.404+09:00  INFO 441273 --- [remittance] [nio-8080-exec-7] c.f.r.transfer.PartnerDispatchService    : partner call attempt=1 partnerRef=P-68c1a73c-b262-4d31-8787-3e7fc462e935 result=FAILED
2026-10-28T21:01:43.707+09:00  INFO 441273 --- [remittance] [nio-8080-exec-7] c.f.r.transfer.PartnerDispatchService    : partner call attempt=2 partnerRef=P-68c1a73c-b262-4d31-8787-3e7fc462e935 result=FAILED
2026-10-28T21:01:44.109+09:00  INFO 441273 --- [remittance] [nio-8080-exec-7] c.f.r.transfer.PartnerDispatchService    : partner call attempt=3 partnerRef=P-68c1a73c-b262-4d31-8787-3e7fc462e935 result=FAILED
```

**S9: final state**

Request:
```
GET /api/transfers/68c1a73c-b262-4d31-8787-3e7fc462e935
```
Status: 200
Response:
```json
{"transferId":"68c1a73c-b262-4d31-8787-3e7fc462e935","status":"FAILED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-28T12:01:43.183Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-68c1a73c-b262-4d31-8787-3e7fc462e935","failureReason":"partner rejected 3 attempts"}
```

### Reproduce Part 2

Use a freshly started app: after one S7 run, the C002 limit stays used until a restart.

```
java -jar target/remittance-0.0.1-SNAPSHOT.jar
./scripts/scenarios-part2.sh                  # BASE defaults to http://localhost:8080
```

With Docker:

```
docker compose up -d app
docker compose run --rm -e SCRIPT=scenarios-part2.sh scenarios
```

With `podman-compose`: `podman-compose --profile tools run --rm -e SCRIPT=scenarios-part2.sh scenarios`.

Single calls with curl (replace `<transferId>`):

```
curl -s -X POST http://localhost:8080/api/transfers/<transferId>/send
curl -s -X POST http://localhost:8080/api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"P-<transferId>","eventId":"evt_0001","status":"COMPLETED"}'
```
