package com.finshot.remittance.error;

/** 404 NOT_FOUND */
public class NotFoundException extends ApiException {
	public NotFoundException(String message) {
		super(ErrorCode.NOT_FOUND, message); // uses the 404 code
	}
}
