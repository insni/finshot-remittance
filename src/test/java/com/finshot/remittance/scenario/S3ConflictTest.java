package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S3: Same retry signal as S1, with the amount changed to 600000. */
@DisplayName("S3 Same retry signal as S1, amount changed to 600000")
class S3ConflictTest extends ScenarioTest {

	@Test
	void S3_same_key_with_changed_amount_returns_409() {
		String key = newKey(); // same retry key
		var first = create("C001", "500000", key); // original request
		assertEquals(HttpStatus.CREATED, first.getStatusCode()); // 201
		var changed = create("C001", "600000", key); // request with only the amount changed
		assertEquals(HttpStatus.CONFLICT, changed.getStatusCode()); // 409
		assertEquals("CONFLICT", changed.getBody().get("error").asText()); // error code
		assertEquals(1, rows("C001", key)); // no new row
		String id = first.getBody().get("transferId").asText(); // original transfer ID
		assertEquals("500000", get(id).getBody().get("sendAmount").asText()); // amount unchanged
	}
}
