package com.finshot.remittance.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/** transfers 테이블 한 행. 금액은 생성 시 한 번 계산해 저장하고 읽을 때 다시 계산하지 않는다. */
record Transfer(
		String transferId, // UUID 문자열
		String customerId, // 송금 고객
		String idempotencyKey, // 재시도 키
		String requestHash, // 요청 내용 SHA-256
		String sendCurrency, // 보내는 통화
		String receiveCurrency, // 받는 통화
		BigDecimal sendAmount, // 보내는 금액 (원)
		BigDecimal fee, // 수수료 (원)
		BigDecimal totalDebit, // 총 출금액 (원)
		BigDecimal receiveAmount, // 받는 금액 (PHP, 소수 2자리)
		String recipientName, // 수취인
		TransferStatus status, // 현재 상태
		Instant createdAt, // 생성 시각
		Instant updatedAt) { // 변경 시각
}
