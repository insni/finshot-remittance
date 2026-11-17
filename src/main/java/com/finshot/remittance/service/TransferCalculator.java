package com.finshot.remittance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The only place for amount rules. Rounding happens here, once, at creation. All BigDecimal. */
final class TransferCalculator {

	private static final BigDecimal FEE_RATE = new BigDecimal("0.01"); // fee rate 1%
	private static final BigDecimal MIN_FEE = new BigDecimal("3000"); // minimum fee 3,000 KRW
	private static final BigDecimal KRW_TO_PHP = new BigDecimal("0.0412"); // hard-coded exchange rate

	record Amounts(BigDecimal fee, BigDecimal totalDebit, BigDecimal receiveAmount) { // calculation result
	}

	static Amounts calculate(BigDecimal sendAmount) {
		BigDecimal fee = sendAmount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP).max(MIN_FEE); // 1% rounded HALF_UP to whole KRW, at least 3000
		BigDecimal receive = sendAmount.multiply(KRW_TO_PHP).setScale(2, RoundingMode.HALF_UP); // apply rate, 2 decimals HALF_UP
		return new Amounts(fee, sendAmount.add(fee), receive); // total debit = amount + fee
	}
}
