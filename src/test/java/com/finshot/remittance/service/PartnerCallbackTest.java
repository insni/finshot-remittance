package com.finshot.remittance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.domain.TransferStatus;
import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.repository.TransferRepository;

@SpringBootTest
class PartnerCallbackTest {

	@Autowired
	TransferService transfers; // 송금 생성용

	@Autowired
	TransferRepository repository; // SENDING 상태 만들기용

	@Autowired
	PartnerCallbackService callbacks; // 시험 대상

	@Autowired
	JdbcTemplate jdbc; // 기록 수 확인용

	@Test
	void 같은_eventId_콜백_두_번은_한_번만_반영하고_둘_다_성공한다() {
		Transfer t = transfers.create(UUID.randomUUID().toString(), new CreateTransferRequest("C001", "KRW", "20000", "PHP", "Juan Dela Cruz")); // 새 송금
		String ref = "P-" + t.transferId(); // 파트너 참조값
		assertEquals(1, repository.startSending(t.transferId(), ref, TransferService.now())); // SENDING으로 만든다 (파트너 호출은 생략)
		var callback = new PartnerCallbackRequest(ref, "evt_" + t.transferId(), "COMPLETED"); // 같은 콜백

		assertEquals(TransferStatus.COMPLETED, callbacks.receive(callback).status()); // 첫 전달: COMPLETED
		assertEquals(TransferStatus.COMPLETED, callbacks.receive(callback).status()); // 둘째 전달: 에러 없이 현재 상태

		var other = new PartnerCallbackRequest(ref, "evt2_" + t.transferId(), "COMPLETED"); // 다른 eventId로 이미 COMPLETED인 송금에
		assertThrows(ConflictException.class, () -> callbacks.receive(other)); // SENDING이 아니라 409

		Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM callback_events WHERE partner_ref = ?", Integer.class, ref);
		assertEquals(1, events, "이벤트 기록은 한 행"); // 중복 전달도, 409로 실패한 이벤트도 기록이 남지 않음(롤백)
	}
}
