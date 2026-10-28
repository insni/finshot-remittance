package com.finshot.remittance.transfer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** transfers·customers 접근(SQL)만 담당. 판단은 서비스에 둔다. */
@Repository
class TransferRepository {

	private static final String COLUMNS = "transfer_id, customer_id, idempotency_key, request_hash, send_currency, receive_currency, "
			+ "send_amount, fee, total_debit, receive_amount, recipient_name, status, created_at, updated_at"; // 조회·삽입 공통 컬럼

	private static final RowMapper<Transfer> MAPPER = (rs, i) -> new Transfer( // 한 행을 Transfer로
			rs.getString("transfer_id"), rs.getString("customer_id"), rs.getString("idempotency_key"), rs.getString("request_hash"), // 식별 정보
			rs.getString("send_currency"), rs.getString("receive_currency"), // 통화
			rs.getBigDecimal("send_amount"), rs.getBigDecimal("fee"), rs.getBigDecimal("total_debit"), rs.getBigDecimal("receive_amount"), // 금액은 BigDecimal 그대로
			rs.getString("recipient_name"), TransferStatus.valueOf(rs.getString("status")), // 수취인·상태
			rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), // UTC로 저장한 시각을 되읽음
			rs.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)); // 변경 시각

	private final JdbcTemplate jdbc; // 스프링이 주입하는 JDBC 도우미

	TransferRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc; // 주입값 보관
	}

	boolean customerExists(String customerId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE customer_id = ?", Integer.class, customerId) > 0; // 고객 존재 여부
	}

	/** 중복 (고객, 키)면 DuplicateKeyException이 그대로 올라간다. */
	void insert(Transfer t) {
		jdbc.update("INSERT INTO transfers (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", // 한 문장 삽입
				t.transferId(), t.customerId(), t.idempotencyKey(), t.requestHash(), t.sendCurrency(), t.receiveCurrency(), // 식별·통화
				t.sendAmount(), t.fee(), t.totalDebit(), t.receiveAmount(), t.recipientName(), t.status().name(), // 금액·상태
				utc(t.createdAt()), utc(t.updatedAt())); // 시각은 UTC 기준 저장
	}

	Optional<Transfer> findById(String transferId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM transfers WHERE transfer_id = ?", MAPPER, transferId).stream().findFirst(); // ID로 한 건
	}

	Optional<Transfer> findByCustomerAndKey(String customerId, String idempotencyKey) {
		return jdbc.query("SELECT " + COLUMNS + " FROM transfers WHERE customer_id = ? AND idempotency_key = ?", MAPPER, // 고객별 키로 조회
				customerId, idempotencyKey).stream().findFirst(); // 있으면 한 건
	}

	/** 조건부 UPDATE 한 문장. 현재 상태가 from일 때만 바뀌므로 동시 요청에도 한 번만 성공. 바뀐 행 수를 돌려준다. */
	int transition(String transferId, TransferStatus from, TransferStatus to, Instant now) {
		if (!from.canMoveTo(to)) {
			throw new IllegalArgumentException(from + " -> " + to + " is not an allowed transition"); // 전이 표 위반은 프로그램 오류
		}
		return jdbc.update("UPDATE transfers SET status = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // 상태 조건 포함
				to.name(), utc(now), transferId, from.name()); // 0행이면 없거나 상태가 다름
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); // 서버 시간대와 무관하게 UTC 벽시계 값으로
	}
}
