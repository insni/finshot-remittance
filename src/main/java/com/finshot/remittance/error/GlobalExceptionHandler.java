package com.finshot.remittance.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** The only place that turns every error into the {"error","message"} format. Stack traces never go in the body. */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class); // for server logs

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return respond(e.code(), e.getMessage()); // business exceptions keep their own code
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
	ResponseEntity<ErrorResponse> handleBadBody(Exception e) {
		return respond(ErrorCode.VALIDATION_ERROR, "request body must be valid JSON"); // malformed JSON or wrong type gives 400
	}

	@ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
	ResponseEntity<ErrorResponse> handleUnknownRoute(Exception e) {
		return respond(ErrorCode.NOT_FOUND, "no such endpoint"); // unknown path or disallowed method gives 404
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("unexpected error", e); // details go to the log only
		return respond(ErrorCode.INTERNAL_ERROR, "unexpected server error"); // generic message only in the body
	}

	private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
		return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), message)); // build status and body
	}
}
