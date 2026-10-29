package com.finshot.remittance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TransferCalculatorTest {

	private static TransferCalculator.Amounts calc(String amount) {
		return TransferCalculator.calculate(new BigDecimal(amount)); // 문자열 금액을 BigDecimal로 계산
	}

	@Test
	void 과제_예시_500000() {
		var a = calc("500000"); // 과제에 나온 예시
		assertEquals("5000", a.fee().toPlainString()); // 수수료 1%
		assertEquals("505000", a.totalDebit().toPlainString()); // 금액 + 수수료
		assertEquals("20600.00", a.receiveAmount().toPlainString()); // 끝자리 0 포함 소수 2자리
	}

	@Test
	void 최소_수수료_3000과_HALF_UP_경계() {
		assertEquals("3000", calc("10000").fee().toPlainString()); // 1%=100 이지만 최소 3000
		assertEquals("412.00", calc("10000").receiveAmount().toPlainString()); // 10000 x 0.0412
		assertEquals("3000", calc("250050").fee().toPlainString()); // 1%=2500.5 -> 반올림해도 3000 미만이라 최소 3000
		assertEquals("3501", calc("350050").fee().toPlainString()); // 1%=3500.5 -> HALF_UP으로 3501
		assertEquals("14422.06", calc("350050").receiveAmount().toPlainString()); // 14422.06 (소수 4자리에서 2자리로)
	}
}
