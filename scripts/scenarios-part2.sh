#!/usr/bin/env bash
# Runs scenarios S7 to S9 (Part 2) in order against a freshly started app (localhost:8080).
# Usage: terminal 1: ./mvnw spring-boot:run, terminal 2: ./scripts/scenarios-part2.sh
# Keys get a different RUN prefix on every run, so re-running against the same app does not reuse keys. C002's daily limit stays used while the app is up, so check S7 on a fresh app.
BASE="${BASE:-http://localhost:8080}"
up=; for _ in $(seq 60); do curl -s -o /dev/null "$BASE/" && up=1 && break; sleep 1; done  # poll every 1 second, up to 60 times, until the server is up
[[ -n "$up" ]] || { echo "no response from server: $BASE" >&2; exit 1; }  # stop if it never comes up
RUN="${RUN:-$(date +%s)}"
MAX_TRIES=50

# call <title> <METHOD> <path> [Idempotency-Key] [body]  : same output format as call in scenarios.sh
call() {
  local title="$1" method="$2" path="$3" key="${4:-}" body="${5:-}"
  local args=(-s -X "$method" "$BASE$path" -w '\n%{http_code}')  # actual curl arguments (-w to read the status code)
  local show=("curl -i -X $method $BASE$path")  # curl command to print, one entry per line
  if [[ -n "$body" ]]; then args+=(-H 'Content-Type: application/json'); show+=("-H 'Content-Type: application/json'"); fi  # JSON header if there is a body
  if [[ -n "$key" ]]; then args+=(-H "Idempotency-Key: $key"); show+=("-H 'Idempotency-Key: $key'"); fi  # idempotency header if there is a key
  if [[ -n "$body" ]]; then args+=(-d "$body"); show+=("-d '$body'"); fi  # -d if there is a body
  echo "### $title"
  echo "Request:"
  echo '```'
  printf '%s' "${show[0]}"  # first line
  for line in "${show[@]:1}"; do printf ' \\\n  %s' "$line"; done  # continuation lines: join with ` \` and indent two spaces
  echo
  echo '```'
  local out; out="$(curl "${args[@]}")"  # run: body, then the status code on the last line
  LAST_BODY="${out%$'\n'*}"  # everything before the status code is the response body
  echo "Status: ${out##*$'\n'}"  # the last line is the status code
  echo "Response:"
  echo '```json'
  echo "$LAST_BODY"
  echo '```'
  echo
}

# build the request body: body <customer> <amount>
body() {
  echo "{\"customerId\":\"$1\",\"sendCurrency\":\"KRW\",\"sendAmount\":\"$2\",\"receiveCurrency\":\"PHP\",\"recipientName\":\"Juan Dela Cruz\"}"
}

# extract a string field from the response body (LAST_BODY): field <name>
field() {
  echo "$LAST_BODY" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

# create a new transfer and call /send: create_and_send <title prefix> <customer> <key> -> ID, LAST_BODY (send response)
create_and_send() {
  call "$1 create" POST /api/transfers "$3" "$(body "$2" 100000)"
  ID="$(field transferId)"
  call "$1 send" POST "/api/transfers/$ID/send"
}

# S7: fill C002 up to the limit (3,000,000) and go over. 3 x 1,000,000 pass (a total equal to the limit is allowed), the 4th gets 422
for i in 1 2 3 4; do
  call "S7-$i C002 create 1000000" POST /api/transfers "s7-$RUN-$i" "$(body C002 1000000)"
done

# S8: try until the partner accepts a transfer (SENDING) -> send the same callback (same eventId) twice -> fetch the final state
for n in $(seq $MAX_TRIES); do
  create_and_send "S8 try $n" C001 "s8-$RUN-$n"
  [[ "$(field status)" == "SENDING" ]] && break
done
PREF="$(field partnerRef)"
echo "S8: reached SENDING after $n transfer(s)"; echo
CALLBACK="{\"partnerRef\":\"$PREF\",\"eventId\":\"evt_$RUN\",\"status\":\"COMPLETED\"}"
call "S8a callback, first delivery" POST /api/callbacks/partner "" "$CALLBACK"
call "S8b same callback again (same eventId)" POST /api/callbacks/partner "" "$CALLBACK"
call "S8c final state" GET "/api/transfers/$ID"

# S9: try until all 3 attempts fail and the transfer becomes FAILED. The attempt records (attempts) are in the send response
for n in $(seq $MAX_TRIES); do
  create_and_send "S9 try $n" C003 "s9-$RUN-$n"
  [[ "$(field status)" == "FAILED" ]] && break
done
echo "S9: reached FAILED after $n transfer(s)"; echo
call "S9 final state" GET "/api/transfers/$ID"
