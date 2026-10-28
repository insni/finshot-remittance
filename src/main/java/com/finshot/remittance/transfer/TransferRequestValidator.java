package com.finshot.remittance.transfer;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.finshot.remittance.error.ValidationException;

/** 생성 요청의 형식 검증. 규칙 목록을 순서대로 보고 처음 어긋난 것의 메시지로 400을 낸다. */
@Component
class TransferRequestValidator {

	private static final Pattern DIGITS = Pattern.compile("^[0-9]+$"); // 숫자만 (음수·소수·문자 거부)
	private static final BigInteger MIN_AMOUNT = BigInteger.valueOf(10_000); // 최소 송금액
	private static final BigInteger MAX_AMOUNT = BigInteger.TEN.pow(12); // 상한: DB DECIMAL(15,2) 오버플로 방지용 방어선
	private static final int MAX_NAME = 100; // 수취인 최대 길이
	private static final int MAX_KEY = 64; // 멱등성 키 최대 길이

	private record Rule(BooleanSupplier holds, String message) { // 조건과 실패 메시지의 짝
	}

	void validate(String idempotencyKey, CreateTransferRequest r) {
		Stream.of( // 순서 중요: 뒤 규칙은 앞 규칙(null·숫자 형식)이 통과했다는 전제
				new Rule(() -> notBlank(idempotencyKey), "Idempotency-Key header is required"), // 키 필수
				new Rule(() -> idempotencyKey.length() <= MAX_KEY, "Idempotency-Key must be at most 64 characters"), // 키 길이
				new Rule(() -> notBlank(r.customerId()), "customerId is required"), // 고객 ID 필수
				new Rule(() -> "KRW".equals(r.sendCurrency()), "sendCurrency must be KRW"), // 보내는 통화
				new Rule(() -> "PHP".equals(r.receiveCurrency()), "receiveCurrency must be PHP"), // 받는 통화
				new Rule(() -> r.sendAmount() != null && DIGITS.matcher(r.sendAmount()).matches(), "sendAmount must be a string of digits only"), // 형식
				new Rule(() -> amount(r).compareTo(MIN_AMOUNT) >= 0, "sendAmount must be at least 10000"), // 최소 금액 (0 포함)
				new Rule(() -> amount(r).compareTo(MAX_AMOUNT) <= 0, "sendAmount must be at most 1000000000000"), // 상한
				new Rule(() -> notBlank(r.recipientName()), "recipientName is required"), // 수취인 필수
				new Rule(() -> r.recipientName().length() <= MAX_NAME, "recipientName must be at most 100 characters")) // 수취인 길이
				.filter(rule -> !rule.holds().getAsBoolean()) // 어긋난 규칙만 남기고
				.findFirst() // 처음 하나
				.ifPresent(rule -> { throw new ValidationException(rule.message()); }); // 400으로 변환
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank(); // null·공백만 있는 값 거부
	}

	private static BigInteger amount(CreateTransferRequest r) {
		return new BigInteger(r.sendAmount()); // 숫자 형식 통과 후에만 호출됨
	}
}
