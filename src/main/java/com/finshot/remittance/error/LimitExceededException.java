package com.finshot.remittance.error;

/** 422 LIMIT_EXCEEDED */
public class LimitExceededException extends ApiException {
	public LimitExceededException(String message) {
		super(ErrorCode.LIMIT_EXCEEDED, message); // uses the 422 code
	}
}
