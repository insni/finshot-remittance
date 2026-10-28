#!/usr/bin/env bash
# S1~S6 시나리오 실행 스크립트. 새로 띄운 앱(localhost:8080)에 대해 순서대로 실행한다.
# 사용: java -jar target/remittance-0.0.1-SNAPSHOT.jar & ./scripts/scenarios.sh
BASE="${BASE:-http://localhost:8080}"

# call <제목> <METHOD> <경로> [Idempotency-Key] [body]  : 요청 전체와 응답(상태 코드 + body)을 출력하고 body를 LAST_BODY에 담는다
call() {
  local title="$1" method="$2" path="$3" key="${4:-}" body="${5:-}"
  local args=(-s -X "$method" "$BASE$path" -w '\n%{http_code}')
  echo "### $title"
  echo "Request:"
  echo '```'
  echo "$method $path"
  if [[ -n "$body" ]]; then args+=(-H 'Content-Type: application/json' -d "$body"); echo "Content-Type: application/json"; fi
  if [[ -n "$key" ]]; then args+=(-H "Idempotency-Key: $key"); echo "Idempotency-Key: $key"; fi
  [[ -n "$body" ]] && echo "$body"
  echo '```'
  local out; out="$(curl "${args[@]}")"
  LAST_BODY="${out%$'\n'*}"
  echo "Response: ${out##*$'\n'}"
  echo '```'
  echo "$LAST_BODY"
  echo '```'
  echo
}

# 요청 body 만들기: body <금액>
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
