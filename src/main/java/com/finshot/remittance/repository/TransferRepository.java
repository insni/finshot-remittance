package com.finshot.remittance.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.domain.TransferStatus;

/** SQL access to transfers and customers only. Decisions stay in the service. */
@Repository
public class TransferRepository {

	private static final String COLUMNS = "transfer_id, customer_id, idempotency_key, request_hash, send_currency, receive_currency, "
			+ "send_amount, fee, total_debit, receive_amount, recipient_name, status, created_at, updated_at"; // columns for insert
	private static final String SELECT_COLUMNS = COLUMNS + ", partner_ref, failure_reason"; // select columns: adds 2 partner columns (NULL on insert)
	private static final String NOT_COUNTED = "'CANCELLED', 'FAILED'"; // statuses excluded from the daily limit sum

private static final RowMapper<Transfer> MAPPER = (rs, i) -> new Transfer( // row to Transfer
			rs.getString("transfer_id"), rs.getString("customer_id"), rs.getString("idempotency_key"), rs.getString("request_hash"), // ids
			rs.getString("send_currency"), rs.getString("receive_currency"), // currencies
			rs.getBigDecimal("send_amount"), rs.getBigDecimal("fee"), rs.getBigDecimal("total_debit"), rs.getBigDecimal("receive_amount"), // amounts stay BigDecimal
			rs.getString("recipient_name"), TransferStatus.valueOf(rs.getString("status")), // recipient and status
			rs.getString("partner_ref"), rs.getString("failure_reason"), // partner reference and failure reason (null if none)
			rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), // read back the time stored as UTC
			rs.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)); // updated time

	private final JdbcTemplate jdbc; // JDBC helper injected by Spring

	TransferRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean customerExists(String customerId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE customer_id = ?", Integer.class, customerId) > 0; // does the customer exist
	}

	/** A duplicate (customer, key) makes DuplicateKeyException propagate as is. */
	public void insert(Transfer t) {
		jdbc.update("INSERT INTO transfers (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", // insert
				t.transferId(), t.customerId(), t.idempotencyKey(), t.requestHash(), t.sendCurrency(), t.receiveCurrency(), // ids and currencies
				t.sendAmount(), t.fee(), t.totalDebit(), t.receiveAmount(), t.recipientName(), t.status().name(), // amounts and status
				utc(t.createdAt()), utc(t.updatedAt())); // time is stored as UTC
	}

	public Optional<Transfer> findById(String transferId) {
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE transfer_id = ?", MAPPER, transferId).stream().findFirst(); // by ID
	}

	public Optional<Transfer> findByCustomerAndKey(String customerId, String idempotencyKey) {
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE customer_id = ? AND idempotency_key = ?", MAPPER, // lookup by customer and key
				customerId, idempotencyKey).stream().findFirst(); // first match, if any
	}

	public Optional<Transfer> findByPartnerRef(String partnerRef) {
		return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM transfers WHERE partner_ref = ?", MAPPER, partnerRef).stream().findFirst(); // lookup by partner reference (for callbacks)
	}

	/** Locks the customer row until the transaction ends. Create requests of the same customer queue up here. */
	public void lockCustomer(String customerId) {
		jdbc.queryForList("SELECT customer_id FROM customers WHERE customer_id = ? FOR UPDATE", String.class, customerId); // only the row lock is needed
	}

	/** Sum of sendAmount for transfers created in [from, to). Cancelled and failed ones are excluded. */
	public BigDecimal sumSendAmount(String customerId, Instant from, Instant to) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(send_amount), 0) FROM transfers WHERE customer_id = ? " // per customer
				+ "AND created_at >= ? AND created_at < ? AND status NOT IN (" + NOT_COUNTED + ")", BigDecimal.class, // period and status filter
				customerId, utc(from), utc(to)); // times are compared as UTC wall-clock values
	}

	/** One conditional UPDATE. It changes the row only if the status is still from, so only one concurrent request succeeds. Returns the number of changed rows. */
	public int transition(String transferId, TransferStatus from, TransferStatus to, Instant now) {
		requireAllowed(from, to); // check the transition table
		return jdbc.update("UPDATE transfers SET status = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // status condition included
				to.name(), utc(now), transferId, from.name()); // 0 rows means not found or wrong status
	}

	/** REQUESTED -> SENDING and save partner_ref together (saved before the partner call so a callback can always find it). */
	public int startSending(String transferId, String partnerRef, Instant now) {
		requireAllowed(TransferStatus.REQUESTED, TransferStatus.SENDING); // check the transition table
		return jdbc.update("UPDATE transfers SET status = ?, partner_ref = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // status condition included
				TransferStatus.SENDING.name(), partnerRef, utc(now), transferId, TransferStatus.REQUESTED.name()); // 0 rows means not found or not REQUESTED
	}

	/** SENDING -> FAILED and save the reason. If the status changed meanwhile (e.g. COMPLETED), 0 rows change and nothing is overwritten. */
	public int fail(String transferId, String reason, Instant now) {
		requireAllowed(TransferStatus.SENDING, TransferStatus.FAILED); // check the transition table
		return jdbc.update("UPDATE transfers SET status = ?, failure_reason = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", // status condition included
				TransferStatus.FAILED.name(), reason, utc(now), transferId, TransferStatus.SENDING.name()); // only when SENDING
	}

	private static void requireAllowed(TransferStatus from, TransferStatus to) {
		if (!from.canMoveTo(to)) {
			throw new IllegalArgumentException(from + " -> " + to + " is not an allowed transition"); // breaking the transition table is a programming error
		}
	}

	static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); // UTC wall-clock value, independent of the server time zone
	}
}
