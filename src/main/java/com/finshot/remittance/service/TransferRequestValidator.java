package com.finshot.remittance.service;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.error.ValidationException;

/** Format validation of the create request. Checks the rules in order and returns 400 with the message of the first one that fails. */
@Component
class TransferRequestValidator {

	private static final Pattern DIGITS = Pattern.compile("^[0-9]+$"); // digits only (rejects negatives, decimals, letters)
	private static final BigInteger MIN_AMOUNT = BigInteger.valueOf(10_000); // minimum send amount
	private static final BigInteger MAX_AMOUNT = BigInteger.TEN.pow(12); // upper bound: guard against DB DECIMAL(15,2) overflow
	private static final int MAX_NAME = 100; // max recipient name length
	private static final int MAX_KEY = 64; // max idempotency key length

	private record Rule(BooleanSupplier holds, String message) { // condition and its failure message
	}

	void validate(String idempotencyKey, CreateTransferRequest r) {
		Stream.of( // order matters: later rules assume the earlier ones (null, digit format) passed
				new Rule(() -> notBlank(idempotencyKey), "Idempotency-Key header is required"), // key required
				new Rule(() -> idempotencyKey.length() <= MAX_KEY, "Idempotency-Key must be at most 64 characters"), // key length
				new Rule(() -> notBlank(r.customerId()), "customerId is required"), // customerId required
				new Rule(() -> "KRW".equals(r.sendCurrency()), "sendCurrency must be KRW"), // send currency
				new Rule(() -> "PHP".equals(r.receiveCurrency()), "receiveCurrency must be PHP"), // receive currency
				new Rule(() -> r.sendAmount() != null && DIGITS.matcher(r.sendAmount()).matches(), "sendAmount must be a string of digits only"), // format
				new Rule(() -> amount(r).compareTo(MIN_AMOUNT) >= 0, "sendAmount must be at least 10000"), // minimum amount (also covers 0)
				new Rule(() -> amount(r).compareTo(MAX_AMOUNT) <= 0, "sendAmount must be at most 1000000000000"), // upper bound
				new Rule(() -> notBlank(r.recipientName()), "recipientName is required"), // recipient required
				new Rule(() -> r.recipientName().length() <= MAX_NAME, "recipientName must be at most 100 characters")) // recipient length
				.filter(rule -> !rule.holds().getAsBoolean()) // keep only failed rules
				.findFirst() // first one
				.ifPresent(rule -> { throw new ValidationException(rule.message()); }); // turn it into 400
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank(); // reject null or blank
	}

	private static BigInteger amount(CreateTransferRequest r) {
		return new BigInteger(r.sendAmount()); // only called after the digit-format rule passed
	}
}
