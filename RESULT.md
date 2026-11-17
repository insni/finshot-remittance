# RESULT

S1-S9, run in order on one freshly started app (2026-10-31). Requests are complete curl commands.

## How to run

Start from a machine with nothing installed except a container engine. The Docker path needs no Java, no Maven and no database: the database is H2, which runs inside the app.

### Requirements

- Docker Engine with Compose v2 (check: `docker compose version`), or Podman with `podman-compose` (check: `podman-compose --version`).
- Internet access on the first run: the images and the Maven dependencies are downloaded.
- Port 8080 must be free on the host.

### 1. Open the project folder

Unzip the submission or clone the repository, then open a terminal in the folder that contains `compose.yaml`:

```
cd finshot-remittance
```

### 2. Build and start the app with Docker Compose

```
docker compose up -d --build app
```

The first run takes a few minutes: it downloads the base images and builds the app with Maven inside the image.
The app listens on `http://localhost:8080`. Check that it has started:

```
docker compose logs app | grep "Started RemittanceApplication"
```

The database starts empty, with customers C001, C002 and C003 from `schema.sql` and no transfers.

### 3. Run the scenarios in order

Run them on the app from step 2, in this order. Each command prints its output to the terminal; the scenario scripts wait up to 60 seconds for the app before they start.

With Docker Compose v2:

```
docker compose run --rm scenarios                                  # S1-S6
docker compose run --rm -e SCRIPT=scenarios-part2.sh scenarios     # S7-S9
```

With `podman-compose` (for example on Fedora, where `docker compose` runs podman-compose): `--profile tools` must come before `run`, because the `scenarios` and `test` services belong to the `tools` profile:

```
podman-compose --profile tools run --rm scenarios                                  # S1-S6
podman-compose --profile tools run --rm -e SCRIPT=scenarios-part2.sh scenarios     # S7-S9
```

Without `--profile tools`, podman-compose reports `missing services [scenarios]`.

The scenario containers reach the app as `http://app:8080`. To send the calls by hand, paste the curl commands from the Request blocks below, top to bottom, against `http://localhost:8080`.
Some URLs and bodies contain ids from earlier responses, so use your own: S4b and S5 use the `transferId` from S4a, and S8 and S9 use the ids from their own create and send responses.

### 4. Run again, or stop

To run the scenarios again from an empty database, restart the app and repeat step 3:

```
docker compose restart app
```

A restart empties the database. Without a restart, the keys K1-K3 return the transfers from the first run, and the C002 daily limit stays used.

To stop and remove the containers:

```
docker compose down
```

### Alternative: run with Maven (JDK 17 or newer)

Only if you have JDK 17+ installed. The Maven wrapper downloads Maven and the dependencies on the first run:

```
./mvnw spring-boot:run          # terminal 1, keep it running
./scripts/scenarios.sh          # terminal 2, S1-S6
./scripts/scenarios-part2.sh    # terminal 2, S7-S9
```

The scripts need `bash` and `curl` on the host. To run again, stop the app (Ctrl+C) and start it again.

### Troubleshooting

- Port 8080 in use: stop the other program on 8080, or change the `ports` line in `compose.yaml`.
- The app does not start: run `docker compose logs app` and read the last lines.
- Scenario output shows `Connection refused`: the app is not ready yet. Wait for the `Started RemittanceApplication` line, then run again.


## Part 1: S1-S6

### S1 create C001 500000
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K1' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"5c0e15af-0b26-4df8-8ba3-d9cb004b2ee9","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-31T05:58:19.180Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S2 retry same request, same key
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K1' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"5c0e15af-0b26-4df8-8ba3-d9cb004b2ee9","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-31T05:58:19.180Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S3 same key, different amount
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K1' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"600000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 409
Response:
```json
{"error":"CONFLICT","message":"Idempotency-Key was already used with a different request"}
```

### S4a create another transfer
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K2' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"3e6eea5d-ec47-481f-9906-a1457925ea3c","status":"REQUESTED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-31T05:58:19.246Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S4b cancel it
Request:
```
curl -i -X POST http://localhost:8080/api/transfers/3e6eea5d-ec47-481f-9906-a1457925ea3c/cancel
```
Status: 200
Response:
```json
{"transferId":"3e6eea5d-ec47-481f-9906-a1457925ea3c","status":"CANCELLED","sendAmount":"500000","fee":"5000","totalDebit":"505000","receiveAmount":"20600.00","createdAt":"2026-10-31T05:58:19.246Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S5 cancel again
Request:
```
curl -i -X POST http://localhost:8080/api/transfers/3e6eea5d-ec47-481f-9906-a1457925ea3c/cancel
```
Status: 409
Response:
```json
{"error":"CONFLICT","message":"cannot cancel a transfer in status CANCELLED"}
```

### S6 sendAmount 5000
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: K3' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"5000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 400
Response:
```json
{"error":"VALIDATION_ERROR","message":"sendAmount must be at least 10000"}
```

## Part 2: S7-S9

`partnerRef` is `"P-" + transferId`, and `transferId` is a random UUID, so whether the partner accepts a transfer changes from run to run. The script therefore creates and sends transfers until it gets the case it needs. S8 needs `SENDING`; this run got it on the first try. S9 needs `FAILED`; this run got it on the third try. Tries 1-2 went to `SENDING` and are not shown. S7-1 and S7-2 send the same body as S7-3 with keys `-1` and `-2`, and both returned 201.

### S7-3 last success (total reaches 3000000)
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s7-1793426299-3' \
  -d '{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"e288d36a-b789-4992-bb25-c2aec7ba7d81","status":"REQUESTED","sendAmount":"1000000","fee":"10000","totalDebit":"1010000","receiveAmount":"41200.00","createdAt":"2026-10-31T05:58:19.552Z","customerId":"C002","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S7-4 rejected (daily limit)
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s7-1793426299-4' \
  -d '{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 422
Response:
```json
{"error":"LIMIT_EXCEEDED","message":"daily limit of 3000000 KRW exceeded: already 3000000 today (Asia/Seoul)"}
```

### S8 setup: create
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s8-1793426299-1' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","status":"REQUESTED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:19.597Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S8 setup: send
Request:
```
curl -i -X POST http://localhost:8080/api/transfers/94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2/send
```
Status: 200
Response:
```json
{"transferId":"94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","status":"SENDING","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:19.597Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","attempts":[{"attemptNo":1,"result":"ACCEPTED","attemptedAt":"2026-10-31T05:58:19.814Z"}]}
```

### S8a callback, first delivery
Request:
```
curl -i -X POST http://localhost:8080/api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","eventId":"evt_1793426299","status":"COMPLETED"}'
```
Status: 200
Response:
```json
{"transferId":"94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:19.597Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2"}
```

### S8b same callback again (same eventId)
Request:
```
curl -i -X POST http://localhost:8080/api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","eventId":"evt_1793426299","status":"COMPLETED"}'
```
Status: 200
Response:
```json
{"transferId":"94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:19.597Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2"}
```

### S8c final state
Request:
```
curl -i -X GET http://localhost:8080/api/transfers/94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2
```
Status: 200
Response:
```json
{"transferId":"94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2","status":"COMPLETED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:19.597Z","customerId":"C001","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-94a7c2fb-d69d-4bdd-b26c-cb06bb83dac2"}
```

### S9 create
Request:
```
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s9-1793426299-3' \
  -d '{"customerId":"C003","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```
Status: 201
Response:
```json
{"transferId":"37bf8843-9e06-406b-9e63-d1a2303523e1","status":"REQUESTED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:20.447Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

### S9 send (retried 3 times, FAILED)
Request:
```
curl -i -X POST http://localhost:8080/api/transfers/37bf8843-9e06-406b-9e63-d1a2303523e1/send
```
Status: 200
Response:
```json
{"transferId":"37bf8843-9e06-406b-9e63-d1a2303523e1","status":"FAILED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:20.447Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-37bf8843-9e06-406b-9e63-d1a2303523e1","failureReason":"partner rejected 3 attempts","attempts":[{"attemptNo":1,"result":"FAILED","attemptedAt":"2026-10-31T05:58:20.680Z"},{"attemptNo":2,"result":"FAILED","attemptedAt":"2026-10-31T05:58:20.981Z"},{"attemptNo":3,"result":"FAILED","attemptedAt":"2026-10-31T05:58:21.382Z"}]}
```

### S9 final state
Request:
```
curl -i -X GET http://localhost:8080/api/transfers/37bf8843-9e06-406b-9e63-d1a2303523e1
```
Status: 200
Response:
```json
{"transferId":"37bf8843-9e06-406b-9e63-d1a2303523e1","status":"FAILED","sendAmount":"100000","fee":"3000","totalDebit":"103000","receiveAmount":"4120.00","createdAt":"2026-10-31T05:58:20.447Z","customerId":"C003","sendCurrency":"KRW","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"P-37bf8843-9e06-406b-9e63-d1a2303523e1","failureReason":"partner rejected 3 attempts"}
```

### S9 retry log

App log for `P-37bf8843-9e06-406b-9e63-d1a2303523e1`:

```
2026-10-31T14:58:20.681+09:00  INFO 49395 --- [remittance] [nio-8080-exec-7] c.f.r.service.PartnerDispatchService     : partner call attempt=1 partnerRef=P-37bf8843-9e06-406b-9e63-d1a2303523e1 result=FAILED
2026-10-31T14:58:20.982+09:00  INFO 49395 --- [remittance] [nio-8080-exec-7] c.f.r.service.PartnerDispatchService     : partner call attempt=2 partnerRef=P-37bf8843-9e06-406b-9e63-d1a2303523e1 result=FAILED
2026-10-31T14:58:21.383+09:00  INFO 49395 --- [remittance] [nio-8080-exec-7] c.f.r.service.PartnerDispatchService     : partner call attempt=3 partnerRef=P-37bf8843-9e06-406b-9e63-d1a2303523e1 result=FAILED
```
