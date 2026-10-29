package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S3: S1과 같은 재시도 신호로, 금액만 600000으로 바꿔 전송. */
@DisplayName("S3 같은 재시도 신호로 금액만 600000으로 바꿔 전송")
class S3ConflictTest extends ScenarioTest {

	@Test
	void S3_같은_키로_금액만_바꾸면_409() {
		String key = newKey(); // 같은 재시도 키
		var first = create("C001", "500000", key); // 원래 요청
		assertEquals(HttpStatus.CREATED, first.getStatusCode()); // 201
		var changed = create("C001", "600000", key); // 금액만 바꾼 요청
		assertEquals(HttpStatus.CONFLICT, changed.getStatusCode()); // 409
		assertEquals("CONFLICT", changed.getBody().get("error").asText()); // 오류 코드
		assertEquals(1, rows("C001", key)); // 새 행은 안 생김
		String id = first.getBody().get("transferId").asText(); // 원래 송금 ID
		assertEquals("500000", get(id).getBody().get("sendAmount").asText()); // 금액은 그대로
	}
}
