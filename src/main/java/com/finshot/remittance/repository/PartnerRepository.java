package com.finshot.remittance.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.finshot.remittance.partner.PartnerAttempt;

/** partner_attempts·callback_events 접근(SQL)만 담당. */
@Repository
public class PartnerRepository {

	private static final RowMapper<PartnerAttempt> ATTEMPT_MAPPER = (rs, i) -> new PartnerAttempt( // 한 행을 PartnerAttempt로
			rs.getInt("attempt_no"), rs.getString("partner_ref"), rs.getString("result"), // 번호·참조값·결과
			rs.getObject("attempted_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)); // UTC로 저장한 시각을 되읽음

	private final JdbcTemplate jdbc; // 스프링이 주입하는 JDBC 도우미

	PartnerRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc; // 주입값 보관
	}

	public void insertAttempt(String transferId, PartnerAttempt a) {
		jdbc.update("INSERT INTO partner_attempts (transfer_id, partner_ref, attempt_no, result, attempted_at) VALUES (?,?,?,?,?)", // 시도 한 줄 기록
				transferId, a.partnerRef(), a.attemptNo(), a.result(), TransferRepository.utc(a.attemptedAt())); // 시각은 UTC 기준 저장
	}

	public List<PartnerAttempt> findAttempts(String transferId) {
		return jdbc.query("SELECT attempt_no, partner_ref, result, attempted_at FROM partner_attempts WHERE transfer_id = ? ORDER BY attempt_no", // 시도 순서대로
				ATTEMPT_MAPPER, transferId);
	}

	/** 같은 eventId가 이미 있으면 DuplicateKeyException이 그대로 올라간다. */
	public void insertCallbackEvent(String eventId, String partnerRef, String status, Instant now) {
		jdbc.update("INSERT INTO callback_events (event_id, partner_ref, status, received_at) VALUES (?,?,?,?)", // 수신 기록
				eventId, partnerRef, status, TransferRepository.utc(now)); // 시각은 UTC 기준 저장
	}
}
