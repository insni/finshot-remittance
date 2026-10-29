package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S6: sendAmount = 5000으로 생성. */
@DisplayName("S6 sendAmount = 5000으로 생성")
class S6ValidationTest extends ScenarioTest {

	@Test
	void S6_5000원은_400_VALIDATION_ERROR() {
		String key = newKey(); // 이번 시험 전용 키
		var res = create("C001", "5000", key); // 최소 금액 미만
		assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode()); // 400
		assertEquals("VALIDATION_ERROR", res.getBody().get("error").asText()); // 오류 코드
		assertEquals(0, rows("C001", key)); // 저장되지 않음
	}
}
