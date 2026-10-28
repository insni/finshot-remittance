package com.finshot.remittance.error;

/** 400 VALIDATION_ERROR */
public class ValidationException extends ApiException {
	public ValidationException(String message) {
		super(ErrorCode.VALIDATION_ERROR, message); // 400 코드로 생성
	}
}
