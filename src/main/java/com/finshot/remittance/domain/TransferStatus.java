package com.finshot.remittance.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Transfer statuses and the allowed-transition table. Any transition not in the table is rejected (409). */
public enum TransferStatus {
	REQUESTED, SENDING, COMPLETED, FAILED, CANCELLED;

	private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED = Map.of( // allowed transitions
			REQUESTED, EnumSet.of(SENDING, CANCELLED), // REQUESTED -> SENDING or CANCELLED
			SENDING, EnumSet.of(COMPLETED, FAILED)); // SENDING -> COMPLETED or FAILED

	public boolean canMoveTo(TransferStatus next) {
		return ALLOWED.getOrDefault(this, Set.of()).contains(next); // not in the table (terminal status): empty set
	}
}
