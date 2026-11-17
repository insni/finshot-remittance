package com.finshot.remittance.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the transfers table. Amounts are calculated once at creation, stored, and never recalculated on read. */
public record Transfer(
		String transferId, // UUID string
		String customerId, // customer
		String idempotencyKey, // idempotency key
		String requestHash, // SHA-256 of the request content
		String sendCurrency, // send currency
		String receiveCurrency, // receive currency
		BigDecimal sendAmount, // send amount (KRW)
		BigDecimal fee, // fee (KRW)
		BigDecimal totalDebit, // total debit (KRW)
		BigDecimal receiveAmount, // receive amount (PHP, 2 decimals)
		String recipientName, // recipient
		TransferStatus status, // current status
		String partnerRef, // partner reference (null before SENDING)
		String failureReason, // failure reason (FAILED only)
		Instant createdAt, // created time
		Instant updatedAt) { // updated time
}
