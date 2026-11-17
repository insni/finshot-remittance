package com.finshot.remittance.error;

import org.springframework.http.HttpStatus;

/** Pairs an error code with an HTTP status. Defined by the assignment: VALIDATION_ERROR/NOT_FOUND/CONFLICT/LIMIT_EXCEEDED (INTERNAL_ERROR is added for unexpected errors). */
public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST), // 400 validation failed
	NOT_FOUND(HttpStatus.NOT_FOUND), // 404 not found
	CONFLICT(HttpStatus.CONFLICT), // 409 status or idempotency conflict
	LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY), // 422 daily limit exceeded (Part 2)
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR); // 500 unexpected error

	private final HttpStatus status; // matching HTTP status

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
