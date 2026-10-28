package com.finshot.remittance.error;

/** 응답 코드를 품은 업무 예외의 공통 부모. 하위 3개가 에러 코드별 타입. */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode code; // 이 예외가 나타내는 에러 코드

	protected ApiException(ErrorCode code, String message) {
		super(message); // 사람이 읽는 메시지
		this.code = code; // 코드 보관
	}

	public ErrorCode code() {
		return code; // 코드 반환
	}
}
