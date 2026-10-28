package com.finshot.remittance.transfer;

import java.time.Instant;

/** partner_attempts 테이블 한 행: 파트너 호출 한 번의 기록. */
record PartnerAttempt(
		int attemptNo, // 시도 번호 (1~3)
		String partnerRef, // 사용한 파트너 참조값
		String result, // ACCEPTED 또는 FAILED
		Instant attemptedAt) { // 시도 시각
}
