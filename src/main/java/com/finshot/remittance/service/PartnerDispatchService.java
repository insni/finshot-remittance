package com.finshot.remittance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.error.NotFoundException;
import com.finshot.remittance.partner.PartnerAttempt;
import com.finshot.remittance.partner.PartnerClient;
import com.finshot.remittance.repository.PartnerRepository;
import com.finshot.remittance.repository.TransferRepository;

/**
 * 송금을 파트너에게 보내는 흐름. SENDING 전이와 partner_ref 저장은 파트너 호출보다 먼저, 호출 중엔 DB 트랜잭션·잠금을 잡지 않는다.
 * 파트너 접수는 완료가 아니다: 접수되면 SENDING 그대로 두고 콜백을 기다린다.
 */
@Service
public class PartnerDispatchService {

	private static final Logger log = LoggerFactory.getLogger(PartnerDispatchService.class); // 시도 로그용
	private static final long[] BACKOFF_MS = {100, 200}; // 1->2회, 2->3회 사이 대기. 길이+1 = 총 시도 횟수
	private static final String FAILURE_REASON = "partner rejected " + (BACKOFF_MS.length + 1) + " attempts"; // FAILED 사유

	private final TransferRepository transfers; // 송금 SQL
	private final PartnerRepository partners; // 시도 기록 SQL
	private final PartnerClient client; // 가짜 파트너

	PartnerDispatchService(TransferRepository transfers, PartnerRepository partners, PartnerClient client) {
		this.transfers = transfers; // 주입값 보관
		this.partners = partners; // 주입값 보관
		this.client = client; // 주입값 보관
	}

	/** 동기 실행: 끝나면 SENDING(접수) 또는 FAILED(3회 실패)인 송금과 시도 기록을 돌려준다. */
	public TransferResponse send(String transferId) {
		String partnerRef = "P-" + transferId; // 송금마다 고정: 재시도해도 같은 값이라 파트너에 중복이 없다
		if (transfers.startSending(transferId, partnerRef, TransferService.now()) == 0) { // 1) 조건부 UPDATE 한 문장 (자체로 원자적)
			Transfer current = transfers.findById(transferId).orElseThrow(() -> new NotFoundException("transfer not found: " + transferId)); // 없으면 404
			throw new ConflictException("cannot send a transfer in status " + current.status()); // 있으면 409
		}
		boolean accepted = callPartner(transferId, partnerRef); // 2) 트랜잭션 밖에서 최대 3회 호출
		if (!accepted) {
			transfers.fail(transferId, FAILURE_REASON, TransferService.now()); // 3) SENDING일 때만 FAILED, 0행이면 덮어쓰지 않음
		}
		Transfer latest = transfers.findById(transferId).orElseThrow(); // 그 사이 콜백이 왔을 수 있어 현재 값을 다시 읽음
		return TransferResponse.from(latest, partners.findAttempts(transferId)); // 시도 기록을 응답에 포함
	}

	/** 접수되거나 횟수를 다 쓸 때까지 호출. 시도마다 DB와 로그에 남긴다. */
	private boolean callPartner(String transferId, String partnerRef) {
		for (int attemptNo = 1; attemptNo <= BACKOFF_MS.length + 1; attemptNo++) { // 총 3회
			boolean accepted = client.submit(partnerRef); // 파트너 호출
			String result = accepted ? "ACCEPTED" : "FAILED"; // 결과 문자열
			partners.insertAttempt(transferId, new PartnerAttempt(attemptNo, partnerRef, result, TransferService.now())); // 시도 기록
			log.info("partner call attempt={} partnerRef={} result={}", attemptNo, partnerRef, result); // 시도 번호·partnerRef·결과 로그
			if (accepted) {
				return true; // 접수되면 끝
			}
			if (attemptNo <= BACKOFF_MS.length) {
				backoff(BACKOFF_MS[attemptNo - 1]); // 다음 시도 전 대기
			}
		}
		return false; // 3회 모두 실패
	}

	private static void backoff(long millis) {
		try {
			Thread.sleep(millis); // 100ms, 200ms 순서
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // 인터럽트 상태 복원
			throw new IllegalStateException("interrupted while waiting to retry the partner call", e); // 중단 신호는 삼키지 않음
		}
	}
}
