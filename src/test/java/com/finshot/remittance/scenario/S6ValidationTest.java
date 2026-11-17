package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S6: Create with sendAmount = 5000. */
@DisplayName("S6 Create with sendAmount = 5000")
class S6ValidationTest extends ScenarioTest {

	@Test
	void S6_amount_5000_returns_400_VALIDATION_ERROR() {
		String key = newKey(); // key used only by this test
		var res = create("C001", "5000", key); // below the minimum amount
		assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode()); // 400
		assertEquals("VALIDATION_ERROR", res.getBody().get("error").asText()); // error code
		assertEquals(0, rows("C001", key)); // nothing saved
	}
}
