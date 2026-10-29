package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S5: 같은 송금을 다시 취소. */
@DisplayName("S5 같은 송금을 다시 취소")
class S5CancelAgainTest extends ScenarioTest {

	@Test
	void S5_취소한_송금을_다시_취소하면_409() {
		String id = create("C001", "30000", newKey()).getBody().get("transferId").asText(); // 새 송금
		assertEquals(HttpStatus.OK, cancel(id).getStatusCode()); // 첫 취소 200
		var again = cancel(id); // 다시 취소
		assertEquals(HttpStatus.CONFLICT, again.getStatusCode()); // 409
		assertEquals("CONFLICT", again.getBody().get("error").asText()); // 오류 코드
		assertEquals("CANCELLED", get(id).getBody().get("status").asText()); // 상태는 그대로 취소
	}
}
