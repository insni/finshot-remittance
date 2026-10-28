#!/usr/bin/env bash
# S7~S9 시나리오 실행 스크립트 (Part 2). 새로 띄운 앱(localhost:8080)에 대해 순서대로 실행한다.
# 사용: java -jar target/remittance-0.0.1-SNAPSHOT.jar & ./scripts/scenarios-part2.sh
# 키에는 실행마다 다른 RUN 접두어가 붙어 같은 앱에 다시 돌려도 키가 겹치지 않는다. 단 C002의 일일 한도는 앱이 떠 있는 동안 남으므로 S7은 새 앱에서 본다.
BASE="${BASE:-http://localhost:8080}"
RUN="${RUN:-$(date +%s)}"
MAX_TRIES=50

# call <제목> <METHOD> <경로> [Idempotency-Key] [body]  : scenarios.sh의 call과 같은 출력 형식 (Part 1 출력을 그대로 두려고 복사)
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

# 요청 body 만들기: body <고객> <금액>
body() {
  echo "{\"customerId\":\"$1\",\"sendCurrency\":\"KRW\",\"sendAmount\":\"$2\",\"receiveCurrency\":\"PHP\",\"recipientName\":\"Juan Dela Cruz\"}"
}

# 응답 body(LAST_BODY)에서 문자열 필드 꺼내기: field <이름>
field() {
  echo "$LAST_BODY" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

# 새 송금을 만들고 /send 까지 한다: create_and_send <제목접두어> <고객> <키> -> ID, LAST_BODY(send 응답)
create_and_send() {
  call "$1 create" POST /api/transfers "$3" "$(body "$2" 100000)"
  ID="$(field transferId)"
  call "$1 send" POST "/api/transfers/$ID/send"
}

# S7: C002로 한도(3,000,000)까지 채우고 넘기기. 100만 x 3은 통과(합계가 한도와 같은 것까지 허용), 4번째는 422
for i in 1 2 3 4; do
  call "S7-$i C002 create 1000000" POST /api/transfers "s7-$RUN-$i" "$(body C002 1000000)"
done

# S8: 파트너가 접수한 송금(SENDING)을 찾을 때까지 시도 -> 같은 콜백(같은 eventId) 두 번 -> 최종 조회
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

# S9: 3회 모두 실패해 FAILED가 될 때까지 시도. 시도 기록(attempts)은 send 응답에 들어 있다
for n in $(seq $MAX_TRIES); do
  create_and_send "S9 try $n" C003 "s9-$RUN-$n"
  [[ "$(field status)" == "FAILED" ]] && break
done
echo "S9: reached FAILED after $n transfer(s)"; echo
call "S9 final state" GET "/api/transfers/$ID"
