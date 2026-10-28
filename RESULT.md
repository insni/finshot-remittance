# RESULT

S1-S6 were run in order against a freshly started app on 2026-10-28.
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

All six scenarios behaved as expected.

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

### S7-S9

Not attempted. Part 2 was not done.
