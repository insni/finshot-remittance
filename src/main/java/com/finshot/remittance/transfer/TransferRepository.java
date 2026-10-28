package com.finshot.remittance.transfer;

import java.math.BigDecimal;
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
			+ "send_amount, fee, total_debit, receive_amount, recipient_name, status, created_at, updated_at"; // 삽입 컬럼
	private static final String SELECT_COLUMNS = COLUMNS + ", partner_ref, failure_reason"; // 조회 컬럼: 파트너 관련 2개 추가 (삽입 땐 NULL)
	private static final String NOT_COUNTED = "'CANCELLED', 'FAILED'"; // 일일 한도 합산에서 빼는 상태

	private static final RowMapper<Transfer> MAPPER = (rs, i) -> new Transfer( // 한 행을 Transfer로
			rs.getString("transfer_id"), rs.getString("customer_id"), rs.getString("idempotency_key"), rs.getString("request_hash"), // 식별 정보
			rs.getString("send_currency"), rs.getString("receive_currency"), // 통화
			rs.getBigDecimal("send_amount"), rs.getBigDecimal("fee"), rs.getBigDecimal("total_debit"), rs.getBigDecimal("receive_amount"), // 금액은 BigDecimal 그대로
			rs.getString("recipient_name"), TransferStatus.valueOf(rs.getString("status")), // 수취인·상태
			rs.getString("partner_ref"), rs.getString("failure_reason"), // 파트너 참조값·실패 사유 (없으면 null)
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
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE transfer_id = ?", MAPPER, transferId).stream().findFirst(); // ID로 한 건
	}

	Optional<Transfer> findByCustomerAndKey(String customerId, String idempotencyKey) {
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE customer_id = ? AND idempotency_key = ?", MAPPER, // 고객별 키로 조회
				customerId, idempotencyKey).stream().findFirst(); // 있으면 한 건
	}

	Optional<Transfer> findByPartnerRef(String partnerRef) {
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE partner_ref = ?", MAPPER, partnerRef).stream().findFirst(); // 콜백용 참조값 조회
	}

	/** 고객 행을 잠근다(트랜잭션이 끝날 때까지). 같은 고객의 생성 요청이 여기서 한 줄로 선다. */
	void lockCustomer(String customerId) {
		jdbc.queryForList("SELECT customer_id FROM customers WHERE customer_id = ? FOR UPDATE", String.class, customerId); // 행 잠금만 필요
	}

	/** 기간 [from, to) 안에 만든 송금의 sendAmount 합. 취소·실패 건은 뺀다. */
	BigDecimal sumSendAmount(String customerId, Instant from, Instant to) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(send_amount), 0) FROM transfers WHERE customer_id = ? " // 고객별
				+ "AND created_at >= ? AND created_at < ? AND status NOT IN (" + NOT_COUNTED + ")", BigDecimal.class, // 기간·상태 조건
				customerId, utc(from), utc(to)); // 시각은 UTC 벽시계 값으로 비교
	}

	/** 조건부 UPDATE 한 문장. 현재 상태가 from일 때만 바뀌므로 동시 요청에도 한 번만 성공. 바뀐 행 수를 돌려준다. */
	int transition(String transferId, TransferStatus from, TransferStatus to, Instant now) {
		requireAllowed(from, to); // 전이 표 확인
		return jdbc.update("UPDATE transfers SET status = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // 상태 조건 포함
				to.name(), utc(now), transferId, from.name()); // 0행이면 없거나 상태가 다름
	}

	/** REQUESTED -> SENDING 과 동시에 partner_ref 저장 (파트너 호출 전에 저장해야 콜백이 항상 찾는다). */
	int startSending(String transferId, String partnerRef, Instant now) {
		requireAllowed(TransferStatus.REQUESTED, TransferStatus.SENDING); // 전이 표 확인
		return jdbc.update("UPDATE transfers SET status = ?, partner_ref = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // 상태 조건 포함
				TransferStatus.SENDING.name(), partnerRef, utc(now), transferId, TransferStatus.REQUESTED.name()); // 0행이면 없거나 REQUESTED 아님
	}

	/** SENDING -> FAILED 와 사유 저장. 그 사이 상태가 바뀌었으면(예: COMPLETED) 0행이라 덮어쓰지 않는다. */
	int fail(String transferId, String reason, Instant now) {
		requireAllowed(TransferStatus.SENDING, TransferStatus.FAILED); // 전이 표 확인
		return jdbc.update("UPDATE transfers SET status = ?, failure_reason = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // 상태 조건 포함
				TransferStatus.FAILED.name(), reason, utc(now), transferId, TransferStatus.SENDING.name()); // SENDING일 때만
	}

	private static void requireAllowed(TransferStatus from, TransferStatus to) {
		if (!from.canMoveTo(to)) {
			throw new IllegalArgumentException(from + " -> " + to + " is not an allowed transition"); // 전이 표 위반은 프로그램 오류
		}
	}

	static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); // 서버 시간대와 무관하게 UTC 벽시계 값으로
	}
}
