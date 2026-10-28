package com.finshot.remittance.error;

import org.springframework.http.HttpStatus;

/** 에러 코드와 HTTP 상태의 짝. 과제 정의: VALIDATION_ERROR/NOT_FOUND/CONFLICT (INTERNAL_ERROR는 예상 밖 오류용 추가). */
public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST), // 400 검증 실패
	NOT_FOUND(HttpStatus.NOT_FOUND), // 404 대상 없음
	CONFLICT(HttpStatus.CONFLICT), // 409 상태·멱등성 충돌
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR); // 500 예상 밖 오류

	private final HttpStatus status; // 대응하는 HTTP 상태

	ErrorCode(HttpStatus status) {
		this.status = status; // 상태 보관
	}

	public HttpStatus status() {
		return status; // 상태 반환
	}
}
