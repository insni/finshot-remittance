package com.finshot.remittance.error;

/** 409 CONFLICT */
public class ConflictException extends ApiException {
	public ConflictException(String message) {
		super(ErrorCode.CONFLICT, message); // uses the 409 code
	}
}
