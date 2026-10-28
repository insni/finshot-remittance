package com.finshot.remittance.transfer;

/** 응답 body. 금액은 전부 문자열 (receiveAmount는 항상 소수 2자리). */
record TransferResponse(
		String transferId, // 송금 ID
		String status, // 상태
		String sendAmount, // 보내는 금액
		String fee, // 수수료
		String totalDebit, // 총 출금액
		String receiveAmount, // 받는 금액
		String createdAt, // 생성 시각 ISO-8601
		String customerId, // 고객 ID
		String sendCurrency, // 보내는 통화
		String receiveCurrency, // 받는 통화
		String recipientName) { // 수취인

	static TransferResponse from(Transfer t) {
		return new TransferResponse(t.transferId(), t.status().name(), t.sendAmount().toPlainString(), // 저장값을 그대로 문자열화
				t.fee().toPlainString(), t.totalDebit().toPlainString(), t.receiveAmount().toPlainString(), // 지수 표기 방지
				t.createdAt().toString(), t.customerId(), t.sendCurrency(), t.receiveCurrency(), t.recipientName()); // Instant는 ISO-8601
	}
}
