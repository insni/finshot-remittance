package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S2: Retry of S1. Assume no response arrived and send the exact same request again. */
@DisplayName("S2 Retry of S1 - send the exact same request again")
class S2RetryTest extends ScenarioTest {

	@Test
	void S2_same_request_again_returns_same_transfer_and_creates_no_new_one() {
		String key = newKey(); // same retry key
		var first = create("C001", "500000", key); // first request
		var retry = create("C001", "500000", key); // identical retry
		assertEquals(HttpStatus.CREATED, first.getStatusCode()); // first request 201
		assertEquals(HttpStatus.CREATED, retry.getStatusCode()); // retry also 201
		assertEquals(first.getBody().get("transferId"), retry.getBody().get("transferId")); // same transfer
		assertEquals(1, rows("C001", key)); // one row in the DB
	}

	@Test
	void S2_16_concurrent_identical_requests_create_only_one_transfer() throws Exception {
		int threads = 16; // number of concurrent requests
		String key = newKey(); // all use the same key
		var start = new CountDownLatch(1); // start signal
		var pool = Executors.newFixedThreadPool(threads); // thread pool
		try {
			var futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> { // each thread
				start.await(); // waits for the signal
				return create("C001", "10000", key); // then sends the same request
			})).toList();
			start.countDown(); // release all threads at once
			var ids = new HashSet<String>(); // collects distinct transfer IDs
			for (var f : futures) {
				var res = f.get(); // an exception fails the test
				assertEquals(HttpStatus.CREATED, res.getStatusCode()); // all 201
				ids.add(res.getBody().get("transferId").asText()); // collect ID
			}
			assertEquals(1, ids.size(), "all responses must have the same transferId"); // only one transfer
			assertEquals(1, rows("C001", key)); // only one DB row
		} finally {
			pool.shutdownNow(); // clean up the pool
		}
	}
}
