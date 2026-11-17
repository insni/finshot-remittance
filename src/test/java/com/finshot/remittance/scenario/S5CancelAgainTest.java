package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S5: Cancel the same transfer again. */
@DisplayName("S5 Cancel the same transfer again")
class S5CancelAgainTest extends ScenarioTest {

	@Test
	void S5_cancelling_a_cancelled_transfer_returns_409() {
		String id = create("C001", "30000", newKey()).getBody().get("transferId").asText(); // new transfer
		assertEquals(HttpStatus.OK, cancel(id).getStatusCode()); // first cancel 200
		var again = cancel(id); // cancel again
		assertEquals(HttpStatus.CONFLICT, again.getStatusCode()); // 409
		assertEquals("CONFLICT", again.getBody().get("error").asText()); // error code
		assertEquals("CANCELLED", get(id).getBody().get("status").asText()); // status stays CANCELLED
	}
}
