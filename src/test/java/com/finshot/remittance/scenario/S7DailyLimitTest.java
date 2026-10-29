package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S7: C002로 일일 한도를 초과할 때까지 송금 제출. 마지막 성공과 거절을 표시. */
@DisplayName("S7 C002로 일일 한도를 초과할 때까지 송금 제출")
class S7DailyLimitTest extends ScenarioTest {

	@Test
	void S7_C002가_100만원씩_3번은_성공하고_4번째는_422() {
		for (int i = 1; i <= 3; i++) {
			assertEquals(HttpStatus.CREATED, create("C002", "1000000", newKey()).getStatusCode(), i + "번째 성공"); // 3번까지 통과
		}
		var over = create("C002", "1000000", newKey()); // 4번째: 합계 400만
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, over.getStatusCode()); // 422
		assertEquals("LIMIT_EXCEEDED", over.getBody().get("error").asText()); // 오류 코드
	}

	@Test
	void S7_동시에_8건을_보내도_한도만큼만_통과한다() throws Exception {
		int threads = 8; // 100만 x 8 = 800만 시도, 한도는 300만
		var start = new CountDownLatch(1); // 출발 신호
		var pool = Executors.newFixedThreadPool(threads); // 스레드 풀
		try {
			var futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> { // 각 스레드가
				get("warm-up"); // 연결·경로를 미리 데워 출발 시각 차이를 줄인다 (404 응답은 무시)
				start.await(); // 신호를 기다렸다가
				return create("C003", "1000000", newKey()).getStatusCode(); // 새 키로 생성, 상태 코드 반환
			})).toList();
			start.countDown(); // 동시에 출발
			int created = 0; // 201 개수
			int rejected = 0; // 422 개수
			for (var f : futures) {
				var status = f.get(); // 예외가 나면 시험 실패
				created += status == HttpStatus.CREATED ? 1 : 0; // 통과
				rejected += status == HttpStatus.UNPROCESSABLE_ENTITY ? 1 : 0; // 거절
			}
			assertEquals(3, created, "300만 한도에 100만짜리는 정확히 3건만 통과"); // 경쟁 없이 3건
			assertEquals(5, rejected); // 나머지 5건은 422
			BigDecimal sum = jdbc.queryForObject("SELECT SUM(send_amount) FROM transfers WHERE customer_id = 'C003'",
					BigDecimal.class); // 실제 저장 합계
			assertTrue(sum.compareTo(new BigDecimal("3000000")) <= 0, "DB 합계가 한도 이하여야 함: " + sum); // 한도 이하
		} finally {
			pool.shutdownNow(); // 풀 정리
		}
	}
}
