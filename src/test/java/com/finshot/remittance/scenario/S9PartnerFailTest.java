package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.finshot.remittance.partner.PartnerClient;

/** S9: Create a transfer whose partnerRef makes the partner fail. */
@DisplayName("S9 Create a transfer whose partnerRef makes the partner fail")
class S9PartnerFailTest extends ScenarioTest {

	@Autowired
	PartnerClient client; // to check the partner rule directly

	@Test
	void S9_partner_rejecting_all_3_attempts_gives_FAILED_and_3_attempt_records() {
		var body = sendUntil("FAILED", 60); // repeat until a failing transfer is found
		assertEquals("FAILED", body.get("status").asText()); // final status
		assertEquals("partner rejected 3 attempts", body.get("failureReason").asText()); // failure reason
		assertEquals(3, body.get("attempts").size()); // 3 attempt records
		for (int i = 0; i < 3; i++) {
			assertEquals(i + 1, body.get("attempts").get(i).get("attemptNo").asInt()); // in order 1, 2, 3
			assertEquals("FAILED", body.get("attempts").get(i).get("result").asText()); // all rejected
		}
		assertEquals("P-" + body.get("transferId").asText(), body.get("partnerRef").asText()); // reference rule
	}

	@Test
	void S9_partner_rule_gives_the_same_result_for_the_same_reference_and_each_attempt_takes_200ms_or_more() {
		long begin = System.nanoTime(); // start time
		assertFalse(client.submit("d")); // "d".hashCode() = 100 -> 0 < 3, so it fails
		assertFalse(client.submit("d")); // same result when called again (no randomness)
		assertTrue(client.submit("a")); // "a".hashCode() = 97 -> 7, so it is accepted
		long elapsedMs = (System.nanoTime() - begin) / 1_000_000; // time taken by the three calls
		assertTrue(elapsedMs >= 600, "at least 200ms per call: " + elapsedMs); // 3 calls x 200ms
	}
}
