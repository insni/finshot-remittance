package com.finshot.remittance.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.finshot.remittance.dto.CreateTransferRequest;

/** Normalizes the 5 business fields into a SHA-256 hex. Same request gives the same value; any difference gives a different one. */
final class RequestHash {

	static String of(CreateTransferRequest r) {
		String canonical = Stream.of(r.customerId(), r.sendCurrency(), new BigInteger(r.sendAmount()).toString(), // amount normalized to an integer (leading zeros removed)
						r.receiveCurrency(), r.recipientName()) // recipient name as is
				.map(s -> s.length() + ":" + s) // length prefix removes field-boundary ambiguity
				.collect(Collectors.joining("|")); // join into one string
		return sha256Hex(canonical);
	}

	private static String sha256Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); // compute SHA-256
			return HexFormat.of().formatHex(digest); // 64 lowercase hex chars
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // SHA-256 is required on every JVM, so effectively unreachable
		}
	}
}
