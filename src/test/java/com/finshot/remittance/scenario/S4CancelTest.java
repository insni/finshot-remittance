package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S4: Create another transfer, then cancel it. */
@DisplayName("S4 Create another transfer, then cancel it")
class S4CancelTest extends ScenarioTest {

	@Test
	void S4_create_then_cancel_gives_CANCELLED() {
		var created = create("C001", "30000", newKey()); // create another transfer
		assertEquals(HttpStatus.CREATED, created.getStatusCode()); // 201
		assertEquals("REQUESTED", created.getBody().get("status").asText()); // requested status
		String id = created.getBody().get("transferId").asText(); // transfer ID
		var cancelled = cancel(id); // cancel
		assertEquals(HttpStatus.OK, cancelled.getStatusCode()); // 200
		assertEquals("CANCELLED", cancelled.getBody().get("status").asText()); // cancelled
		assertEquals("CANCELLED", get(id).getBody().get("status").asText()); // still cancelled when fetched
	}
}
