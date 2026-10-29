package com.finshot.remittance.dto;

/** POST /api/callbacks/partner 요청 body. 값 검증은 PartnerCallbackService가 맡는다. */
public record PartnerCallbackRequest(
		String partnerRef, // 파트너 참조값
		String eventId, // 이벤트 ID (멱등성 기준)
		String status) { // 알린 상태 (COMPLETED만 허용)
}
