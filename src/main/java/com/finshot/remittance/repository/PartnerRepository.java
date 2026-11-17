package com.finshot.remittance.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.finshot.remittance.partner.PartnerAttempt;

/** SQL access to partner_attempts and callback_events only. */
@Repository
public class PartnerRepository {

	private static final RowMapper<PartnerAttempt> ATTEMPT_MAPPER = (rs, i) -> new PartnerAttempt( // row to PartnerAttempt
			rs.getInt("attempt_no"), rs.getString("partner_ref"), rs.getString("result"), // number, reference, result
			rs.getObject("attempted_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)); // read back the time stored as UTC

	private final JdbcTemplate jdbc; // JDBC helper injected by Spring

	PartnerRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insertAttempt(String transferId, PartnerAttempt a) {
		jdbc.update("INSERT INTO partner_attempts (transfer_id, partner_ref, attempt_no, result, attempted_at) VALUES (?,?,?,?,?)", // record one attempt
				transferId, a.partnerRef(), a.attemptNo(), a.result(), TransferRepository.utc(a.attemptedAt())); // time is stored as UTC
	}

	public List<PartnerAttempt> findAttempts(String transferId) {
		return jdbc.query("SELECT attempt_no, partner_ref, result, attempted_at FROM partner_attempts WHERE transfer_id = ? ORDER BY attempt_no", // in attempt order
				ATTEMPT_MAPPER, transferId);
	}

	/** If the same eventId already exists, DuplicateKeyException propagates as is. */
	public void insertCallbackEvent(String eventId, String partnerRef, String status, Instant now) {
		jdbc.update("INSERT INTO callback_events (event_id, partner_ref, status, received_at) VALUES (?,?,?,?)", // record the received event
				eventId, partnerRef, status, TransferRepository.utc(now)); // time is stored as UTC
	}
}
