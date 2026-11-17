#!/usr/bin/env bash
# Runs scenarios S1 to S6 in order against a freshly started app (localhost:8080).
# Usage: terminal 1: ./mvnw spring-boot:run, terminal 2: ./scripts/scenarios.sh
BASE="${BASE:-http://localhost:8080}"
up=; for _ in $(seq 60); do curl -s -o /dev/null "$BASE/" && up=1 && break; sleep 1; done  # poll every 1 second, up to 60 times, until the server is up
[[ -n "$up" ]] || { echo "no response from server: $BASE" >&2; exit 1; }  # stop if it never comes up

# call <title> <METHOD> <path> [Idempotency-Key] [body]  : prints the request (curl command) and response (status code + body), and stores the body in LAST_BODY
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

# build the request body: body <amount>
body() {
  echo "{\"customerId\":\"C001\",\"sendCurrency\":\"KRW\",\"sendAmount\":\"$1\",\"receiveCurrency\":\"PHP\",\"recipientName\":\"Juan Dela Cruz\"}"
}

call "S1 create C001 500000" POST /api/transfers K1 "$(body 500000)"
call "S2 retry same request, same key" POST /api/transfers K1 "$(body 500000)"
call "S3 same key, different amount" POST /api/transfers K1 "$(body 600000)"
call "S4a create another transfer" POST /api/transfers K2 "$(body 500000)"
ID="$(echo "$LAST_BODY" | sed -n 's/.*"transferId":"\([^"]*\)".*/\1/p')"
call "S4b cancel it" POST "/api/transfers/$ID/cancel"
call "S5 cancel again" POST "/api/transfers/$ID/cancel"
call "S6 sendAmount 5000" POST /api/transfers K3 "$(body 5000)"
