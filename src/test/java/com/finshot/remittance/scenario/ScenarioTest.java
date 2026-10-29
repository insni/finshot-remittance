package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;

/** 시나리오 테스트 공통 기반: 실제 HTTP로 앱을 호출한다 (모든 시나리오가 같은 컨텍스트를 공유). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트라 Docker 8080과 충돌 없음
abstract class ScenarioTest {

	@Autowired
	TestRestTemplate rest; // 4xx도 예외 없이 응답으로 받는 HTTP 클라이언트

	@Autowired
	JdbcTemplate jdbc; // DB 행 수·합계 확인용

	static String newKey() {
		return UUID.randomUUID().toString(); // 시험마다 새 멱등성 키
	}

	ResponseEntity<JsonNode> create(String customerId, String sendAmount, String key) {
		var headers = new HttpHeaders(); // 요청 헤더
		headers.set("Idempotency-Key", key); // 재시도 신호
		var body = Map.of("customerId", customerId, "sendCurrency", "KRW", "sendAmount", sendAmount, // 요청 본문
				"receiveCurrency", "PHP", "recipientName", "Juan Dela Cruz");
		return rest.postForEntity("/api/transfers", new HttpEntity<>(body, headers), JsonNode.class); // 생성 호출
	}

	ResponseEntity<JsonNode> get(String id) {
		return rest.getForEntity("/api/transfers/" + id, JsonNode.class); // 조회
	}

	ResponseEntity<JsonNode> cancel(String id) {
		return rest.postForEntity("/api/transfers/" + id + "/cancel", null, JsonNode.class); // 취소
	}

	ResponseEntity<JsonNode> send(String id) {
		return rest.postForEntity("/api/transfers/" + id + "/send", null, JsonNode.class); // 파트너 전송 (동기)
	}

	ResponseEntity<JsonNode> callback(String partnerRef, String eventId) {
		var body = Map.of("partnerRef", partnerRef, "eventId", eventId, "status", "COMPLETED"); // 완료 콜백
		return rest.postForEntity("/api/callbacks/partner", body, JsonNode.class); // 콜백 호출
	}

	int rows(String customerId, String key) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE customer_id = ? AND idempotency_key = ?",
				Integer.class, customerId, key); // 그 고객·키의 송금 행 수
	}

	/** 새 송금을 만들어 /send 를 반복하다가, 응답 상태가 wantedStatus 인 첫 응답 body 를 돌려준다. */
	JsonNode sendUntil(String wantedStatus, int maxTries) {
		for (int i = 0; i < maxTries; i++) { // 파트너 성패는 partnerRef 해시로 정해져 고를 수 없어 반복
			String id = create("C001", "10000", newKey()).getBody().get("transferId").asText(); // 새 송금
			JsonNode sent = send(id).getBody(); // 전송 시도
			if (wantedStatus.equals(sent.path("status").asText())) {
				return sent; // 원하는 상태를 만났다
			}
		}
		return fail(wantedStatus + " 상태를 " + maxTries + "번 안에 만나지 못함"); // 확률상 거의 없는 경우
	}
}
