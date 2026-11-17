package com.finshot.remittance.dto;

/** Request body of POST /api/callbacks/partner. Value checks are done by PartnerCallbackService. */
public record PartnerCallbackRequest(
		String partnerRef, // partner reference
		String eventId, // event ID (idempotency key)
		String status) { // reported status (only COMPLETED is allowed)
}
