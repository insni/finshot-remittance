package com.finshot.remittance.error;

/** 404 NOT_FOUND */
public class NotFoundException extends ApiException {
	public NotFoundException(String message) {
		super(ErrorCode.NOT_FOUND, message); // 404 코드로 생성
	}
}
