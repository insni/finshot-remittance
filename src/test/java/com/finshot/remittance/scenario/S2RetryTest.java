package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S2: S1의 재시도 — 응답을 못 받았다고 가정하고 S1과 완전히 동일한 요청을 다시 전송. */
@DisplayName("S2 S1의 재시도 - 완전히 동일한 요청을 다시 전송")
class S2RetryTest extends ScenarioTest {

	@Test
	void S2_같은_요청을_다시_보내면_같은_송금이고_새_건은_안_생긴다() {
		String key = newKey(); // 같은 재시도 키
		var first = create("C001", "500000", key); // 첫 요청
		var retry = create("C001", "500000", key); // 똑같은 재시도
		assertEquals(HttpStatus.CREATED, first.getStatusCode()); // 첫 요청 201
		assertEquals(HttpStatus.CREATED, retry.getStatusCode()); // 재시도도 201
		assertEquals(first.getBody().get("transferId"), retry.getBody().get("transferId")); // 같은 송금
		assertEquals(1, rows("C001", key)); // DB에는 한 행
	}

	@Test
	void S2_동시에_같은_요청_16개는_송금_한_건만_만든다() throws Exception {
		int threads = 16; // 동시 요청 수
		String key = newKey(); // 모두 같은 키
		var start = new CountDownLatch(1); // 출발 신호
		var pool = Executors.newFixedThreadPool(threads); // 스레드 풀
		try {
			var futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> { // 각 스레드가
				start.await(); // 신호를 기다렸다가
				return create("C001", "10000", key); // 같은 요청 전송
			})).toList();
			start.countDown(); // 동시에 출발
			var ids = new HashSet<String>(); // 서로 다른 송금 ID 모음
			for (var f : futures) {
				var res = f.get(); // 예외가 나면 시험 실패
				assertEquals(HttpStatus.CREATED, res.getStatusCode()); // 모두 201
				ids.add(res.getBody().get("transferId").asText()); // ID 수집
			}
			assertEquals(1, ids.size(), "모든 응답이 같은 transferId여야 함"); // 송금은 하나
			assertEquals(1, rows("C001", key)); // DB 행도 하나
		} finally {
			pool.shutdownNow(); // 풀 정리
		}
	}
}
