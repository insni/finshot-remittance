package com.finshot.remittance.dto;

/** Request body of POST /api/transfers. Value checks are done by TransferRequestValidator. */
public record CreateTransferRequest(
		String customerId, // customer ID
		String sendCurrency, // send currency (KRW)
		String sendAmount, // send amount (string)
		String receiveCurrency, // receive currency (PHP)
		String recipientName) { // recipient name
}
