package com.finshot.remittance.dto;

/** POST /api/transfers 요청 body. 값 검증은 TransferRequestValidator가 맡는다. */
public record CreateTransferRequest(
		String customerId, // 고객 ID
		String sendCurrency, // 보내는 통화 (KRW)
		String sendAmount, // 보내는 금액 (문자열)
		String receiveCurrency, // 받는 통화 (PHP)
		String recipientName) { // 수취인 이름
}
