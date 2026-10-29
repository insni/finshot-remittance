package com.finshot.remittance.partner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartnerClientTest {

	private final PartnerClient client = new PartnerClient(); // 스프링 없이 직접 생성

	@Test
	void 같은_참조값은_항상_같은_결과이고_시도는_200ms_이상_걸린다() {
		long begin = System.nanoTime(); // 시작 시각
		assertFalse(client.submit("d")); // "d".hashCode()=100 -> 100 % 10 = 0 < 3 이라 실패
		assertFalse(client.submit("d")); // 다시 불러도 같은 결과 (난수 없음)
		assertTrue(client.submit("a")); // "a".hashCode()=97 -> 7 이라 접수
		long elapsedMs = (System.nanoTime() - begin) / 1_000_000; // 세 번 호출에 걸린 시간
		assertTrue(elapsedMs >= 600, "한 번에 최소 200ms: " + elapsedMs); // 3회 x 200ms
	}
}
