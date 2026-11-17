package com.finshot.remittance.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.partner.PartnerAttempt;

/** Response body. All amounts are strings (receiveAmount always has 2 decimals). Partner fields without a value are left out of the body. */
@JsonInclude(JsonInclude.Include.NON_NULL) // the first 11 fields always have a value, so they are unaffected
public record TransferResponse(
		String transferId, // transfer ID
		String status, // status
		String sendAmount, // send amount
		String fee, // fee
		String totalDebit, // total debit
		String receiveAmount, // receive amount
		String createdAt, // created time, ISO-8601
		String customerId, // customer ID
		String sendCurrency, // send currency
		String receiveCurrency, // receive currency
		String recipientName, // recipient
		String partnerRef, // partner reference (after SENDING)
		String failureReason, // failure reason (when FAILED)
		List<Attempt> attempts) { // partner call attempts (send response only)

	record Attempt(int attemptNo, String result, String attemptedAt) { // response form of one attempt
	}

	public static TransferResponse from(Transfer t) {
		return from(t, null); // without attempts
	}

	public static TransferResponse from(Transfer t, List<PartnerAttempt> attempts) {
		return new TransferResponse(t.transferId(), t.status().name(), t.sendAmount().toPlainString(), // stored values as plain strings
				t.fee().toPlainString(), t.totalDebit().toPlainString(), t.receiveAmount().toPlainString(), // avoids exponent notation
				t.createdAt().toString(), t.customerId(), t.sendCurrency(), t.receiveCurrency(), t.recipientName(), // Instant prints as ISO-8601
				t.partnerRef(), t.failureReason(), // null values are left out of the body
				attempts == null ? null : attempts.stream().map(a -> new Attempt(a.attemptNo(), a.result(), a.attemptedAt().toString())).toList()); // convert attempts
	}
}
