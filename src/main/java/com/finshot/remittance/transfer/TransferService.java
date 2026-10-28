package com.finshot.remittance.transfer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.error.NotFoundException;
import com.finshot.remittance.error.ValidationException;

/** 송금 생성·조회·취소의 흐름. 멱등성은 DB unique 제약에 맡기고 앱 잠금은 쓰지 않는다. */
@Service
class TransferService {

	private static final String IDEM_CONSTRAINT = "uq_transfers_customer_idem_key"; // 멱등성 unique 제약 이름

	private final TransferRepository repository; // SQL 담당
	private final TransferRequestValidator validator; // 형식 검증 담당

	TransferService(TransferRepository repository, TransferRequestValidator validator) {
		this.repository = repository; // 주입값 보관
		this.validator = validator; // 주입값 보관
	}

	Transfer create(String idempotencyKey, CreateTransferRequest request) {
		validator.validate(idempotencyKey, request); // 1) 형식 검증
		if (!repository.customerExists(request.customerId())) {
			throw new ValidationException("unknown customerId"); // 2) 모르는 고객은 400
		}
		String hash = RequestHash.of(request); // 3) 요청 내용 지문
		return repository.findByCustomerAndKey(request.customerId(), idempotencyKey) // 4) 같은 키가 이미 있나
				.map(existing -> replay(existing, hash)) // 있으면 원래 건 반환 또는 409
				.orElseGet(() -> insertOrReplay(newTransfer(idempotencyKey, hash, request))); // 없으면 삽입
	}

	Transfer get(String transferId) {
		return repository.findById(transferId).orElseThrow(() -> new NotFoundException("transfer not found: " + transferId)); // 없으면 404
	}

	Transfer cancel(String transferId) {
		int changed = repository.transition(transferId, TransferStatus.REQUESTED, TransferStatus.CANCELLED, now()); // 조건부 UPDATE 한 문장
		if (changed == 0) { // 아무 행도 안 바뀜: 없거나 REQUESTED가 아님
			Transfer current = get(transferId); // 없으면 여기서 404
			throw new ConflictException("cannot cancel a transfer in status " + current.status()); // 있으면 409
		}
		return get(transferId); // 갱신된 행 반환
	}

	/** 삽입 시도. 동시 요청에 져서 멱등성 제약에 걸리면 이긴 쪽 행을 다시 읽어 같은 비교를 한다. */
	private Transfer insertOrReplay(Transfer candidate) {
		try {
			repository.insert(candidate); // 한 문장 삽입, 중복은 DB가 차단
			return candidate; // 성공하면 방금 만든 건
		} catch (DuplicateKeyException e) {
			if (!isIdempotencyViolation(e)) {
				throw e; // 다른 unique 위반은 우리 소관이 아님
			}
			Transfer winner = repository.findByCustomerAndKey(candidate.customerId(), candidate.idempotencyKey()) // 이긴 쪽 행 재조회
					.orElseThrow(() -> e); // 안 보이면 원래 예외
			return replay(winner, candidate.requestHash()); // 같은 비교 규칙 적용
		}
	}

	/** 같은 키: 내용이 같으면 원래 건, 다르면 409. */
	private Transfer replay(Transfer existing, String hash) {
		if (!existing.requestHash().equals(hash)) {
			throw new ConflictException("Idempotency-Key was already used with a different request"); // 같은 키 다른 내용
		}
		return existing; // 같은 내용이면 원래 건 그대로
	}

	private static boolean isIdempotencyViolation(DuplicateKeyException e) {
		return e.getMessage() != null && e.getMessage().toLowerCase().contains(IDEM_CONSTRAINT); // 제약 이름으로 판별
	}

	/** 새 송금 객체 조립. 금액은 여기서 한 번만 계산한다. */
	private Transfer newTransfer(String idempotencyKey, String hash, CreateTransferRequest r) {
		BigDecimal sendAmount = new BigDecimal(new BigInteger(r.sendAmount())); // 앞자리 0 제거한 정수 금액
		TransferCalculator.Amounts amounts = TransferCalculator.calculate(sendAmount); // 수수료·환산 계산
		Instant now = now(); // 생성 시각
		return new Transfer(UUID.randomUUID().toString(), r.customerId(), idempotencyKey, hash, r.sendCurrency(), r.receiveCurrency(), // 식별·통화
				sendAmount, amounts.fee(), amounts.totalDebit(), amounts.receiveAmount(), r.recipientName(), // 금액·수취인
				TransferStatus.REQUESTED, now, now); // 시작 상태 REQUESTED
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS); // 밀리초로 잘라 DB 저장값과 응답값 일치
	}
}
