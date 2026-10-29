package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S4: 다른 송금을 생성한 뒤 취소. 두 응답 모두 표시. */
@DisplayName("S4 다른 송금을 생성한 뒤 취소")
class S4CancelTest extends ScenarioTest {

	@Test
	void S4_송금을_만들고_취소하면_CANCELLED() {
		var created = create("C001", "30000", newKey()); // 다른 송금 생성
		assertEquals(HttpStatus.CREATED, created.getStatusCode()); // 201
		assertEquals("REQUESTED", created.getBody().get("status").asText()); // 요청 상태
		String id = created.getBody().get("transferId").asText(); // 송금 ID
		var cancelled = cancel(id); // 취소
		assertEquals(HttpStatus.OK, cancelled.getStatusCode()); // 200
		assertEquals("CANCELLED", cancelled.getBody().get("status").asText()); // 취소됨
		assertEquals("CANCELLED", get(id).getBody().get("status").asText()); // 조회해도 취소
	}
}
