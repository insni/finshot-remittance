package com.finshot.remittance.transfer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 송금 상태와 허용 전이 표. 표에 없는 전이는 모두 거부(409). */
public enum TransferStatus {
	REQUESTED, SENDING, COMPLETED, FAILED, CANCELLED;

	private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED = Map.of( // 허용 전이 표
			REQUESTED, EnumSet.of(SENDING, CANCELLED), // 요청됨 -> 전송 중 또는 취소
			SENDING, EnumSet.of(COMPLETED, FAILED)); // 전송 중 -> 완료 또는 실패

	public boolean canMoveTo(TransferStatus next) {
		return ALLOWED.getOrDefault(this, Set.of()).contains(next); // 표에 없으면(종단 상태) 빈 집합
	}
}
