package com.finshot.remittance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 금액 규칙이 모인 유일한 곳. 반올림은 여기서 생성 시 한 번만 한다. 전부 BigDecimal. */
final class TransferCalculator {

	private static final BigDecimal FEE_RATE = new BigDecimal("0.01"); // 수수료율 1%
	private static final BigDecimal MIN_FEE = new BigDecimal("3000"); // 최소 수수료 3,000원
	private static final BigDecimal KRW_TO_PHP = new BigDecimal("0.0412"); // 하드코딩 환율

	record Amounts(BigDecimal fee, BigDecimal totalDebit, BigDecimal receiveAmount) { // 계산 결과 묶음
	}

	static Amounts calculate(BigDecimal sendAmount) {
		BigDecimal fee = sendAmount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP).max(MIN_FEE); // 1% 원 단위 HALF_UP, 최소 3000
		BigDecimal receive = sendAmount.multiply(KRW_TO_PHP).setScale(2, RoundingMode.HALF_UP); // 환율 적용, 소수 2자리 HALF_UP
		return new Amounts(fee, sendAmount.add(fee), receive); // 총 출금 = 금액 + 수수료
	}
}
