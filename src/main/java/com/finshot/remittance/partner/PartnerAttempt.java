package com.finshot.remittance.partner;

import java.time.Instant;

/** One row of the partner_attempts table: one partner call. */
public record PartnerAttempt(
		int attemptNo, // attempt number (1-3)
		String partnerRef, // partner reference used
		String result, // ACCEPTED or FAILED
		Instant attemptedAt) { // attempt time
}
