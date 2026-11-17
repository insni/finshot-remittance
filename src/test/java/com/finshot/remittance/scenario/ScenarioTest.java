package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;

/** Common base for scenario tests: calls the app over real HTTP (all scenarios share one context). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // random port, so no clash with Docker on 8080
abstract class ScenarioTest {

	@Autowired
	TestRestTemplate rest; // HTTP client that returns 4xx as a normal response, not an exception

	@Autowired
	JdbcTemplate jdbc; // for checking DB row counts and totals

	static String newKey() {
		return UUID.randomUUID().toString(); // a new idempotency key for each test
	}

	ResponseEntity<JsonNode> create(String customerId, String sendAmount, String key) {
		var headers = new HttpHeaders(); // request headers
		headers.set("Idempotency-Key", key); // retry signal
		var body = Map.of("customerId", customerId, "sendCurrency", "KRW", "sendAmount", sendAmount, // request body
				"receiveCurrency", "PHP", "recipientName", "Juan Dela Cruz");
		return rest.postForEntity("/api/transfers", new HttpEntity<>(body, headers), JsonNode.class); // create call
	}

	ResponseEntity<JsonNode> get(String id) {
		return rest.getForEntity("/api/transfers/" + id, JsonNode.class); // fetch
	}

	ResponseEntity<JsonNode> cancel(String id) {
		return rest.postForEntity("/api/transfers/" + id + "/cancel", null, JsonNode.class); // cancel
	}

	ResponseEntity<JsonNode> send(String id) {
		return rest.postForEntity("/api/transfers/" + id + "/send", null, JsonNode.class); // send to the partner (synchronous)
	}

	ResponseEntity<JsonNode> callback(String partnerRef, String eventId) {
		var body = Map.of("partnerRef", partnerRef, "eventId", eventId, "status", "COMPLETED"); // completed callback
		return rest.postForEntity("/api/callbacks/partner", body, JsonNode.class); // callback call
	}

	int rows(String customerId, String key) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE customer_id = ? AND idempotency_key = ?",
				Integer.class, customerId, key); // number of transfer rows for that customer and key
	}

	/** Creates a new transfer and calls /send, repeating until the response status equals wantedStatus. Returns that first matching response body. */
	JsonNode sendUntil(String wantedStatus, int maxTries) {
		for (int i = 0; i < maxTries; i++) { // the partner result depends on the partnerRef hash and cannot be chosen, so repeat
			String id = create("C001", "10000", newKey()).getBody().get("transferId").asText(); // new transfer
			JsonNode sent = send(id).getBody(); // try to send
			if (wantedStatus.equals(sent.path("status").asText())) {
				return sent; // found the wanted status
			}
		}
		return fail("did not see status " + wantedStatus + " within " + maxTries + " tries"); // very unlikely
	}
}
