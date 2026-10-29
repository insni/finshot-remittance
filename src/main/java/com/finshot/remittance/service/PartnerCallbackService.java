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

/** 파트너 콜백 처리. 송금이 COMPLETED가 되는 유일한 경로. eventId 기준 멱등. */
@Service
public class PartnerCallbackService {

	private static final int MAX_FIELD = 64; // partnerRef·eventId 컬럼 길이

	private final TransferRepository transfers; // 송금 SQL
	private final PartnerRepository partners; // 콜백 기록 SQL
	private final TransactionTemplate tx; // 이벤트 기록과 상태 전이를 한 트랜잭션으로 묶는 도구

	PartnerCallbackService(TransferRepository transfers, PartnerRepository partners, TransactionTemplate tx) {
		this.transfers = transfers; // 주입값 보관
		this.partners = partners; // 주입값 보관
		this.tx = tx; // 주입값 보관
	}

	public Transfer receive(PartnerCallbackRequest r) {
		validate(r); // 1) 400
		Transfer transfer = transfers.findByPartnerRef(r.partnerRef()) // 2) 모르는 참조값은 404
				.orElseThrow(() -> new NotFoundException("unknown partnerRef: " + r.partnerRef()));
		try {
			tx.executeWithoutResult(status -> apply(transfer.transferId(), r)); // 3) 이벤트 기록 + 전이를 한 트랜잭션으로
		} catch (DuplicateKeyException e) {
			// 같은 eventId가 이미 처리됨(롤백 끝난 뒤 여기 도착): 아무것도 바꾸지 않고 현재 상태로 200
		}
		return transfers.findById(transfer.transferId()).orElseThrow(); // 현재 송금 상태 반환
	}

	/** 이벤트 INSERT가 먼저라 중복 eventId는 전이 시도 전에 DuplicateKeyException으로 끝난다. 전이가 0행이면 409로 이벤트도 롤백. */
	private void apply(String transferId, PartnerCallbackRequest r) {
		partners.insertCallbackEvent(r.eventId(), r.partnerRef(), r.status(), TransferService.now()); // 이벤트 기록 (PK 중복 차단)
		if (transfers.transition(transferId, TransferStatus.SENDING, TransferStatus.COMPLETED, TransferService.now()) == 0) {
			throw new ConflictException("cannot complete a transfer that is not in status SENDING"); // SENDING이 아니면 409
		}
	}

	private static void validate(PartnerCallbackRequest r) {
		if (r.partnerRef() == null || r.partnerRef().isBlank() || r.partnerRef().length() > MAX_FIELD) {
			throw new ValidationException("partnerRef is required (at most 64 characters)"); // 참조값 필수
		}
		if (r.eventId() == null || r.eventId().isBlank() || r.eventId().length() > MAX_FIELD) {
			throw new ValidationException("eventId is required (at most 64 characters)"); // 이벤트 ID 필수
		}
		if (!"COMPLETED".equals(r.status())) {
			throw new ValidationException("status must be COMPLETED"); // 과제: 콜백은 COMPLETED만
		}
	}
}
