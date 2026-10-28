package com.finshot.remittance.transfer;

import org.springframework.stereotype.Component;

/** 가짜 파트너. 실제 서비스를 부르지 않고 partnerRef만으로 성공·실패가 정해진다(난수 없음). */
@Component
class PartnerClient {

	static final long MIN_LATENCY_MS = 200; // 시도 한 번의 최소 소요 시간

	/** 파트너가 요청을 접수하면 true. 접수일 뿐 송금 완료가 아니다. 실패 조건: abs(hashCode) % 10 < 3. */
	boolean submit(String partnerRef) {
		sleepLatency(); // 최소 200ms 소요
		return Math.abs((long) partnerRef.hashCode()) % 10 >= 3; // long으로 계산해 Integer.MIN_VALUE의 abs 음수 문제 회피
	}

	private static void sleepLatency() {
		try {
			Thread.sleep(MIN_LATENCY_MS); // 파트너 응답 지연 흉내
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // 인터럽트 상태 복원
			throw new IllegalStateException("interrupted during partner call", e); // 중단 신호는 삼키지 않음
		}
	}
}
