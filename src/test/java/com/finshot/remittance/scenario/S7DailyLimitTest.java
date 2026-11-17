package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.error.LimitExceededException;
import com.finshot.remittance.service.TransferService;

/** S7: Submit transfers for C002 until the daily limit is exceeded. */
@DisplayName("S7 Submit transfers for C002 until the daily limit is exceeded")
class S7DailyLimitTest extends ScenarioTest {

	@Autowired
	TransferService transferService; // the real service, used to race on the limit without HTTP

	@Test
	void S7_C002_three_transfers_of_1000000_succeed_and_the_fourth_returns_422() {
		for (int i = 1; i <= 3; i++) {
			assertEquals(HttpStatus.CREATED, create("C002", "1000000", newKey()).getStatusCode(), "transfer " + i + " should succeed"); // first 3 pass
		}
		var over = create("C002", "1000000", newKey()); // 4th: total would be 4,000,000
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, over.getStatusCode()); // 422
		assertEquals("LIMIT_EXCEEDED", over.getBody().get("error").asText()); // error code
	}

	@Test
	void S7_8_concurrent_calls_straight_to_the_service_pass_only_up_to_the_limit() throws Exception {
		int threads = 8; // 8 x 1,000,000 = 8,000,000 attempted, limit is 3,000,000
		var start = new CountDownLatch(1); // start signal
		var pool = Executors.newFixedThreadPool(threads); // thread pool
		try {
			var futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> { // each thread
				start.await(); // waits for the signal (enters the service almost at once, no HTTP jitter)
				try {
					transferService.create(newKey(), new CreateTransferRequest("C003", "KRW", "1000000", "PHP", "Juan Dela Cruz")); // create directly with a new key
					return true; // passed
				} catch (LimitExceededException e) {
					return false; // rejected for exceeding the limit (any other exception propagates and fails the test)
				}
			})).toList();
			start.countDown(); // release all threads at once
			int created = 0; // number passed
			int rejected = 0; // number rejected
			for (var f : futures) {
				if (f.get()) { // an exception fails the test
					created++; // passed
				} else {
					rejected++; // rejected
				}
			}
			assertEquals(3, created, "with a 3,000,000 limit exactly 3 transfers of 1,000,000 pass"); // 3, with no race
			assertEquals(5, rejected); // the other 5 are rejected
			BigDecimal sum = jdbc.queryForObject("SELECT SUM(send_amount) FROM transfers WHERE customer_id = 'C003'",
					BigDecimal.class); // actual stored total
			assertTrue(sum.compareTo(new BigDecimal("3000000")) <= 0, "DB total must not exceed the limit: " + sum); // within the limit
		} finally {
			pool.shutdownNow(); // clean up the pool
		}
	}
}
