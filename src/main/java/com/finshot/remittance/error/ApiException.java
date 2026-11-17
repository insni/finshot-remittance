package com.finshot.remittance.error;

/** Common parent of business exceptions that carry an error code. The 4 subclasses are the types per error code. */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode code; // error code this exception stands for

	protected ApiException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}
}
