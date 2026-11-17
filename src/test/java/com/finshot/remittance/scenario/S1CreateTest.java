package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

/** S1: Create a transfer for C001, amount 500000. */
@DisplayName("S1 Create a transfer for C001, amount 500000")
class S1CreateTest extends ScenarioTest {

	@Test
	void S1_create_500000_returns_201_with_computed_amounts() {
		var res = create("C001", "500000", newKey()); // the example from the brief
		var body = res.getBody(); // response body
		assertEquals(HttpStatus.CREATED, res.getStatusCode()); // 201
		assertEquals("REQUESTED", body.get("status").asText()); // initial status
		assertEquals("500000", body.get("sendAmount").asText()); // send amount
		assertEquals("5000", body.get("fee").asText()); // 1% fee
		assertEquals("505000", body.get("totalDebit").asText()); // amount + fee
		assertEquals("20600.00", body.get("receiveAmount").asText()); // keeps 2 decimal places
		assertTrue(body.hasNonNull("transferId")); // has transfer ID
		assertTrue(body.hasNonNull("createdAt")); // has creation time
	}

	@ParameterizedTest(name = "S1 amount {0} -> fee {1}")
	@CsvSource({
			"10000, 3000, 13000, 412.00", // 1% = 100, but the minimum fee is 3000
			"250050, 3000, 253050, 10302.06", // 1% = 2500.5 -> still under 3000 after rounding, so minimum 3000
			"350050, 3501, 353551, 14422.06", // 1% = 3500.5 -> 3501 with HALF_UP
			"10013, 3000, 13013, 412.54" // receive amount 412.5356 -> 412.54 with HALF_UP
	})
	void S1_fee_minimum_and_rounding_boundaries(String sendAmount, String fee, String totalDebit, String receiveAmount) {
		var res = create("C001", sendAmount, newKey()); // create with a boundary amount
		var body = res.getBody(); // response body
		assertEquals(HttpStatus.CREATED, res.getStatusCode()); // 201
		assertEquals(fee, body.get("fee").asText()); // fee
		assertEquals(totalDebit, body.get("totalDebit").asText()); // total debit
		assertEquals(receiveAmount, body.get("receiveAmount").asText()); // receive amount
	}
}
