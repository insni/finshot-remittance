package com.finshot.remittance.transfer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 응답 body. 금액은 전부 문자열 (receiveAmount는 항상 소수 2자리). 값이 없는 파트너 관련 필드는 body에서 뺀다. */
@JsonInclude(JsonInclude.Include.NON_NULL) // 앞 11개는 항상 값이 있어 영향 없음
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
		String recipientName, // 수취인
		String partnerRef, // 파트너 참조값 (SENDING 이후)
		String failureReason, // 실패 사유 (FAILED일 때)
		List<Attempt> attempts) { // 파트너 호출 시도 기록 (send 응답에만)

	record Attempt(int attemptNo, String result, String attemptedAt) { // 시도 한 건의 응답 표현
	}

	static TransferResponse from(Transfer t) {
		return from(t, null); // 시도 기록 없이
	}

	static TransferResponse from(Transfer t, List<PartnerAttempt> attempts) {
		return new TransferResponse(t.transferId(), t.status().name(), t.sendAmount().toPlainString(), // 저장값을 그대로 문자열화
				t.fee().toPlainString(), t.totalDebit().toPlainString(), t.receiveAmount().toPlainString(), // 지수 표기 방지
				t.createdAt().toString(), t.customerId(), t.sendCurrency(), t.receiveCurrency(), t.recipientName(), // Instant는 ISO-8601
				t.partnerRef(), t.failureReason(), // 없으면 null이라 body에서 빠짐
				attempts == null ? null : attempts.stream().map(a -> new Attempt(a.attemptNo(), a.result(), a.attemptedAt().toString())).toList()); // 시도 목록 변환
	}
}
