package com.finshot.remittance.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.finshot.remittance.error.LimitExceededException;

@SpringBootTest
class DailyLimitConcurrencyTest {

	@Autowired
	TransferService service; // 실제 서비스 (H2 인메모리 DB 포함)

	@Autowired
	JdbcTemplate jdbc; // 합계 확인용

	@Test
	void 한도를_넘기는_동시_생성은_한도만큼만_통과한다() throws Exception {
		int threads = 8; // 100만 x 8 = 800만 시도, 한도는 300만
		var start = new CountDownLatch(1); // 출발 신호: 동시에 몰리게 함
		ExecutorService pool = Executors.newFixedThreadPool(threads); // 스레드 풀
		try {
			List<Future<Boolean>> futures = IntStream.range(0, threads)
					.mapToObj(i -> pool.submit(() -> { // 각 스레드가
						start.await(); // 신호를 기다렸다가
						try {
							service.create(UUID.randomUUID().toString(), // 서로 다른 키로 (멱등성과 무관한 한도 경쟁)
									new CreateTransferRequest("C003", "KRW", "1000000", "PHP", "Juan Dela Cruz"));
							return true; // 통과
						} catch (LimitExceededException e) {
							return false; // 422
						}
					})).toList();
			start.countDown(); // 동시에 출발
			long passed = 0;
			for (var f : futures) {
				passed += f.get() ? 1 : 0; // 예외가 나면 시험 실패
			}
			assertEquals(3, passed, "300만 한도에 100만짜리는 정확히 3건만 통과"); // 둘 다 통과하는 경쟁이 없어야 함
			BigDecimal sum = jdbc.queryForObject("SELECT SUM(send_amount) FROM transfers WHERE customer_id = 'C003'", BigDecimal.class);
			assertTrue(sum.compareTo(new BigDecimal("3000000")) <= 0, "DB 합계가 한도 이하여야 함: " + sum); // 실제 저장 합계
		} finally {
			pool.shutdownNow(); // 풀 정리
		}
	}
}
