package com.finshot.remittance.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.domain.TransferStatus;
import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.error.LimitExceededException;
import com.finshot.remittance.error.NotFoundException;
import com.finshot.remittance.error.ValidationException;
import com.finshot.remittance.repository.TransferRepository;

/** 송금 생성·조회·취소의 흐름. 멱등성은 DB unique 제약, 일일 한도는 고객 행 잠금으로 지킨다. */
@Service
public class TransferService {

	private static final String IDEM_CONSTRAINT = "uq_transfers_customer_idem_key"; // 멱등성 unique 제약 이름

	private static final BigDecimal DAILY_LIMIT = new BigDecimal("3000000"); // 고객당 하루 sendAmount 합계 상한 (원)
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul"); // 하루 경계 기준 (UTC+9)

	private final TransferRepository repository; // SQL 담당
	private final TransferRequestValidator validator; // 형식 검증 담당
	private final TransactionTemplate tx; // 잠금·한도 확인·삽입을 한 트랜잭션으로 묶는 도구

	TransferService(TransferRepository repository, TransferRequestValidator validator, TransactionTemplate tx) {
		this.repository = repository; // 주입값 보관
		this.validator = validator; // 주입값 보관
		this.tx = tx; // 주입값 보관
	}

	public Transfer create(String idempotencyKey, CreateTransferRequest request) {
		validator.validate(idempotencyKey, request); // 1) 형식 검증
		if (!repository.customerExists(request.customerId())) {
			throw new ValidationException("unknown customerId"); // 2) 모르는 고객은 400
		}
		String hash = RequestHash.of(request); // 3) 요청 내용 지문
		return repository.findByCustomerAndKey(request.customerId(), idempotencyKey) // 4) 같은 키가 이미 있나
				.map(existing -> replay(existing, hash)) // 있으면 원래 건 반환 또는 409
				.orElseGet(() -> insertOrReplay(newTransfer(idempotencyKey, hash, request))); // 없으면 삽입
	}

	public Transfer get(String transferId) {
		return repository.findById(transferId).orElseThrow(() -> new NotFoundException("transfer not found: " + transferId)); // 없으면 404
	}

	public Transfer cancel(String transferId) {
		int changed = repository.transition(transferId, TransferStatus.REQUESTED, TransferStatus.CANCELLED, now()); // 조건부 UPDATE 한 문장
		if (changed == 0) { // 아무 행도 안 바뀜: 없거나 REQUESTED가 아님
			Transfer current = get(transferId); // 없으면 여기서 404
			throw new ConflictException("cannot cancel a transfer in status " + current.status()); // 있으면 409
		}
		return get(transferId); // 갱신된 행 반환
	}

	/**
	 * 삽입 시도. 트랜잭션은 insertUnderLimit 안에서만 열리고 예외가 나면 롤백된 뒤 여기 catch로 온다.
	 * 그래서 아래 재조회는 항상 트랜잭션 밖이다. 고객 행 잠금 때문에 같은 고객의 동시 요청은 직렬화돼 이 catch는 방어선이다.
	 */
	private Transfer insertOrReplay(Transfer candidate) {
		try {
			return tx.execute(status -> insertUnderLimit(candidate)); // 잠금·한도·삽입을 한 트랜잭션으로
		} catch (DuplicateKeyException e) {
			if (!isIdempotencyViolation(e)) {
				throw e; // 다른 unique 위반은 우리 소관이 아님
			}
			Transfer winner = repository.findByCustomerAndKey(candidate.customerId(), candidate.idempotencyKey()) // 이긴 쪽 행 재조회
					.orElseThrow(() -> e); // 안 보이면 원래 예외
			return replay(winner, candidate.requestHash()); // 같은 비교 규칙 적용
		}
	}

	/** 한 트랜잭션: 고객 행 잠금 -> (잠금 대기 중 먼저 들어간 같은 키 확인) -> 일일 한도 확인 -> 삽입. */
	private Transfer insertUnderLimit(Transfer candidate) {
		repository.lockCustomer(candidate.customerId()); // 같은 고객의 다른 생성 요청은 여기서 기다린다
		return repository.findByCustomerAndKey(candidate.customerId(), candidate.idempotencyKey()) // 대기 중 같은 키가 먼저 들어갔나
				.map(existing -> replay(existing, candidate.requestHash())) // 있으면 한도 확인 없이 원래 건 반환 또는 409
				.orElseGet(() -> {
					checkDailyLimit(candidate); // 초과면 422 (롤백)
					repository.insert(candidate); // 한 문장 삽입
					return candidate; // 방금 만든 건
				});
	}

	/** 오늘(Asia/Seoul) 이미 보낸 합계 + 이번 금액이 상한을 넘으면 422. 취소·실패 건은 합계에서 빠진다. */
	private void checkDailyLimit(Transfer candidate) {
		LocalDate day = candidate.createdAt().atZone(BUSINESS_ZONE).toLocalDate(); // 서울 기준 날짜
		ZonedDateTime dayStart = day.atStartOfDay(BUSINESS_ZONE); // 서울 00:00
		BigDecimal sent = repository.sumSendAmount(candidate.customerId(), dayStart.toInstant(), dayStart.plusDays(1).toInstant()); // [오늘 00:00, 내일 00:00)
		if (sent.add(candidate.sendAmount()).compareTo(DAILY_LIMIT) > 0) {
			throw new LimitExceededException("daily limit of " + DAILY_LIMIT.toPlainString() + " KRW exceeded: already " + sent.toPlainString() + " today (Asia/Seoul)"); // 422
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
				TransferStatus.REQUESTED, null, null, now, now); // 시작 상태 REQUESTED, 파트너 값은 아직 없음
	}

	static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS); // 밀리초로 잘라 DB 저장값과 응답값 일치
	}
}
