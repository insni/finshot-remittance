package com.finshot.remittance.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.finshot.remittance.dto.CreateTransferRequest;

/** 업무 필드 5개를 정규화해 SHA-256 hex로 만든다. 같은 요청이면 같은 값, 하나라도 다르면 다른 값. */
final class RequestHash {

	static String of(CreateTransferRequest r) {
		String canonical = Stream.of(r.customerId(), r.sendCurrency(), new BigInteger(r.sendAmount()).toString(), // 금액은 앞자리 0 제거해 정수로 정규화
						r.receiveCurrency(), r.recipientName()) // 수취인은 그대로
				.map(s -> s.length() + ":" + s) // 길이를 붙여 필드 경계 모호함 제거
				.collect(Collectors.joining("|")); // 하나의 문자열로 결합
		return sha256Hex(canonical); // 해시 반환
	}

	private static String sha256Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); // SHA-256 계산
			return HexFormat.of().formatHex(digest); // 소문자 hex 64자
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // JVM 표준 알고리즘이라 사실상 도달 불가
		}
	}
}
