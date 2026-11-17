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

/** Flow for creating, reading and cancelling transfers. Idempotency is enforced by a DB unique constraint, the daily limit by a customer row lock. */
@Service
public class TransferService {

	private static final String IDEM_CONSTRAINT = "uq_transfers_customer_idem_key"; // name of the idempotency unique constraint

	private static final BigDecimal DAILY_LIMIT = new BigDecimal("3000000"); // daily sendAmount total limit per customer (KRW)
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul"); // zone for the day boundary (UTC+9)

	private final TransferRepository repository; // SQL
	private final TransferRequestValidator validator; // format validation
	private final TransactionTemplate tx; // runs lock, limit check and insert in one transaction

	TransferService(TransferRepository repository, TransferRequestValidator validator, TransactionTemplate tx) {
		this.repository = repository;
		this.validator = validator;
		this.tx = tx;
	}

	public Transfer create(String idempotencyKey, CreateTransferRequest request) {
		validator.validate(idempotencyKey, request); // 1) format validation
		if (!repository.customerExists(request.customerId())) {
			throw new ValidationException("unknown customerId"); // 2) unknown customer gives 400
		}
		String hash = RequestHash.of(request); // 3) request fingerprint
		return repository.findByCustomerAndKey(request.customerId(), idempotencyKey) // 4) does the same key already exist?
				.map(existing -> replay(existing, hash)) // if yes: return the original or 409
				.orElseGet(() -> insertOrReplay(newTransfer(idempotencyKey, hash, request))); // if no: insert
	}

	public Transfer get(String transferId) {
		return repository.findById(transferId).orElseThrow(() -> new NotFoundException("transfer not found: " + transferId)); // 404 if missing
	}

	public Transfer cancel(String transferId) {
		int changed = repository.transition(transferId, TransferStatus.REQUESTED, TransferStatus.CANCELLED, now()); // one conditional UPDATE
		if (changed == 0) { // no row changed: not found or not REQUESTED
			Transfer current = get(transferId); // 404 here if missing
			throw new ConflictException("cannot cancel a transfer in status " + current.status()); // 409 if it exists
		}
		return get(transferId); // return the updated row
	}

	/**
	 * Tries to insert. The transaction lives only inside insertUnderLimit; on an exception it is rolled back before reaching this catch.
	 * So the re-read below is always outside a transaction. The customer row lock already serializes concurrent requests of the same customer, so this catch is only a safety net.
	 */
	private Transfer insertOrReplay(Transfer candidate) {
		try {
			return tx.execute(status -> insertUnderLimit(candidate)); // lock, limit and insert in one transaction
		} catch (DuplicateKeyException e) {
			if (!isIdempotencyViolation(e)) {
				throw e; // other unique violations are not ours to handle
			}
			Transfer winner = repository.findByCustomerAndKey(candidate.customerId(), candidate.idempotencyKey()) // re-read the winner's row
					.orElseThrow(() -> e); // if not visible, rethrow the original
			return replay(winner, candidate.requestHash()); // same comparison rule
		}
	}

	/** One transaction: lock customer row -> (check for the same key inserted while waiting) -> daily limit check -> insert. */
	private Transfer insertUnderLimit(Transfer candidate) {
		repository.lockCustomer(candidate.customerId()); // other create requests of the same customer wait here
		return repository.findByCustomerAndKey(candidate.customerId(), candidate.idempotencyKey()) // was the same key inserted while we waited?
				.map(existing -> replay(existing, candidate.requestHash())) // if yes: return the original or 409, without a limit check
				.orElseGet(() -> {
					checkDailyLimit(candidate); // 422 if over the limit (rollback)
					repository.insert(candidate); // insert
					return candidate; // the transfer just created
				});
	}

	/** 422 if today's (Asia/Seoul) total already sent plus this amount exceeds the limit. Cancelled and failed transfers are not counted. */
	private void checkDailyLimit(Transfer candidate) {
		LocalDate day = candidate.createdAt().atZone(BUSINESS_ZONE).toLocalDate(); // date in Seoul
		ZonedDateTime dayStart = day.atStartOfDay(BUSINESS_ZONE); // 00:00 in Seoul
		BigDecimal sent = repository.sumSendAmount(candidate.customerId(), dayStart.toInstant(), dayStart.plusDays(1).toInstant()); // [today 00:00, tomorrow 00:00)
		if (sent.add(candidate.sendAmount()).compareTo(DAILY_LIMIT) > 0) {
			throw new LimitExceededException("daily limit of " + DAILY_LIMIT.toPlainString() + " KRW exceeded: already " + sent.toPlainString() + " today (Asia/Seoul)"); // 422
		}
	}

	/** Same key: the original if the content is the same, 409 if different. */
	private Transfer replay(Transfer existing, String hash) {
		if (!existing.requestHash().equals(hash)) {
			throw new ConflictException("Idempotency-Key was already used with a different request"); // same key, different content
		}
		return existing; // same content: return the original
	}

	private static boolean isIdempotencyViolation(DuplicateKeyException e) {
		return e.getMessage() != null && e.getMessage().toLowerCase().contains(IDEM_CONSTRAINT); // identified by constraint name
	}

	/** Builds a new transfer. Amounts are calculated here, once. */
	private Transfer newTransfer(String idempotencyKey, String hash, CreateTransferRequest r) {
		BigDecimal sendAmount = new BigDecimal(new BigInteger(r.sendAmount())); // integer amount without leading zeros
		TransferCalculator.Amounts amounts = TransferCalculator.calculate(sendAmount); // calculate fee and conversion
		Instant now = now(); // created time
		return new Transfer(UUID.randomUUID().toString(), r.customerId(), idempotencyKey, hash, r.sendCurrency(), r.receiveCurrency(), // ids and currencies
				sendAmount, amounts.fee(), amounts.totalDebit(), amounts.receiveAmount(), r.recipientName(), // amounts and recipient
				TransferStatus.REQUESTED, null, null, now, now); // starts as REQUESTED, no partner values yet
	}

	static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS); // truncated to milliseconds so the DB value and the response match
	}
}
