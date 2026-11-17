package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S8: Send the same callback (same eventId) twice. */
@DisplayName("S8 Send the same callback (same eventId) twice")
class S8CallbackTest extends ScenarioTest {

	private int events(String partnerRef) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM callback_events WHERE partner_ref = ?", Integer.class,
				partnerRef); // number of callback records for that reference
	}

	@Test
	void S8_same_eventId_callback_twice_returns_200_both_times_and_completes_only_once() {
		String ref = sendUntil("SENDING", 30).get("partnerRef").asText(); // partner reference of a SENDING transfer
		String eventId = "evt_" + newKey(); // same event ID
		var first = callback(ref, eventId); // first delivery
		var second = callback(ref, eventId); // duplicate delivery
		assertEquals(HttpStatus.OK, first.getStatusCode()); // first delivery 200
		assertEquals(HttpStatus.OK, second.getStatusCode()); // duplicate also 200
		assertEquals("COMPLETED", first.getBody().get("status").asText()); // first response status
		assertEquals("COMPLETED", second.getBody().get("status").asText()); // second response has the same status
		assertEquals(1, events(ref)); // one event row
	}

	@Test
	void S8_callback_with_a_new_eventId_on_a_completed_transfer_returns_409() {
		String ref = sendUntil("SENDING", 30).get("partnerRef").asText(); // partner reference of a SENDING transfer
		assertEquals(HttpStatus.OK, callback(ref, "evt_" + newKey()).getStatusCode()); // complete it first
		var late = callback(ref, "evt_" + newKey()); // another callback with a different eventId
		assertEquals(HttpStatus.CONFLICT, late.getStatusCode()); // no longer SENDING, so 409
		assertEquals(1, events(ref)); // the failed event is rolled back, so still one row
	}
}
