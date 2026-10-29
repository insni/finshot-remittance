package com.finshot.remittance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import com.finshot.remittance.dto.CreateTransferRequest;

@SpringBootTest
class TransferConcurrencyTest {

	@Autowired
	TransferService service; // 실제 서비스 (H2 인메모리 DB 포함)

	@Autowired
	JdbcTemplate jdbc; // 행 수 확인용

	@Test
	void 같은_키_동시_생성은_한_건만_만들고_모두_같은_송금을_받는다() throws Exception {
		int threads = 16; // 동시 요청 수
		String key = UUID.randomUUID().toString(); // 이번 시험 전용 키
		var request = new CreateTransferRequest("C001", "KRW", "500000", "PHP", "Juan Dela Cruz"); // 모두 같은 내용
		var start = new CountDownLatch(1); // 출발 신호: 동시에 몰리게 함
		ExecutorService pool = Executors.newFixedThreadPool(threads); // 스레드 풀
		try {
			List<Future<String>> futures = IntStream.range(0, threads)
					.mapToObj(i -> pool.submit(() -> { // 각 스레드가
						start.await(); // 신호를 기다렸다가
						return service.create(key, request).transferId(); // 생성 요청, 송금 ID 반환
					})).toList();
			start.countDown(); // 동시에 출발
			var ids = futures.stream().map(f -> { // 결과 수집
				try {
					return f.get(); // 예외가 나면 시험 실패
				} catch (Exception e) {
					throw new AssertionError(e); // 실패로 변환
				}
			}).distinct().toList(); // 중복 제거
			assertEquals(1, ids.size(), "모든 응답이 같은 transferId여야 함"); // 응답은 하나의 송금
			Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE customer_id = 'C001' AND idempotency_key = ?", Integer.class, key);
			assertEquals(1, rows, "DB에는 한 행만 있어야 함"); // 실제 행도 하나
		} finally {
			pool.shutdownNow(); // 풀 정리
		}
	}
}
