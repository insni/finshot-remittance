package com.finshot.remittance.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.domain.TransferStatus;
import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.error.NotFoundException;
import com.finshot.remittance.error.ValidationException;
import com.finshot.remittance.repository.PartnerRepository;
import com.finshot.remittance.repository.TransferRepository;

/** Partner callback handling. The only path to COMPLETED. Idempotent by eventId. */
@Service
public class PartnerCallbackService {

	private static final int MAX_FIELD = 64; // column length of partnerRef and eventId

	private final TransferRepository transfers; // transfer SQL
	private final PartnerRepository partners; // callback SQL
	private final TransactionTemplate tx; // runs event insert and status change in one transaction

	PartnerCallbackService(TransferRepository transfers, PartnerRepository partners, TransactionTemplate tx) {
		this.transfers = transfers;
		this.partners = partners;
		this.tx = tx;
	}

	public Transfer receive(PartnerCallbackRequest r) {
		validate(r); // 1) 400
		Transfer transfer = transfers.findByPartnerRef(r.partnerRef()) // 2) unknown reference gives 404
				.orElseThrow(() -> new NotFoundException("unknown partnerRef: " + r.partnerRef()));
		try {
			tx.executeWithoutResult(status -> apply(transfer.transferId(), r)); // 3) event insert + transition in one transaction
		} catch (DuplicateKeyException e) {
			// same eventId already processed (arrives here after rollback): change nothing, return 200 with the current state
		}
		return transfers.findById(transfer.transferId()).orElseThrow(); // return current transfer
	}

	/** The event INSERT comes first, so a duplicate eventId ends with DuplicateKeyException before any transition. If the transition changes 0 rows, 409 and the event is rolled back too. */
	private void apply(String transferId, PartnerCallbackRequest r) {
		partners.insertCallbackEvent(r.eventId(), r.partnerRef(), r.status(), TransferService.now()); // save event (primary key blocks duplicates)
		if (transfers.transition(transferId, TransferStatus.SENDING, TransferStatus.COMPLETED, TransferService.now()) == 0) {
			throw new ConflictException("cannot complete a transfer that is not in status SENDING"); // 409 unless SENDING
		}
	}

	private static void validate(PartnerCallbackRequest r) {
		if (r.partnerRef() == null || r.partnerRef().isBlank() || r.partnerRef().length() > MAX_FIELD) {
			throw new ValidationException("partnerRef is required (at most 64 characters)"); // partnerRef required
		}
		if (r.eventId() == null || r.eventId().isBlank() || r.eventId().length() > MAX_FIELD) {
			throw new ValidationException("eventId is required (at most 64 characters)"); // eventId required
		}
		if (!"COMPLETED".equals(r.status())) {
			throw new ValidationException("status must be COMPLETED"); // assignment: callbacks only carry COMPLETED
		}
	}
}
