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

/** 모든 오류를 {"error","message"} 한 형식으로 바꾸는 유일한 지점. 스택 트레이스는 본문에 넣지 않는다. */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class); // 서버 로그용

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return respond(e.code(), e.getMessage()); // 업무 예외는 자기 코드 그대로
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
	ResponseEntity<ErrorResponse> handleBadBody(Exception e) {
		return respond(ErrorCode.VALIDATION_ERROR, "request body must be valid JSON"); // 깨진 JSON·잘못된 타입은 400
	}

	@ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
	ResponseEntity<ErrorResponse> handleUnknownRoute(Exception e) {
		return respond(ErrorCode.NOT_FOUND, "no such endpoint"); // 없는 경로·허용 안 된 메서드는 404
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("unexpected error", e); // 상세는 로그에만
		return respond(ErrorCode.INTERNAL_ERROR, "unexpected server error"); // 본문엔 일반 문구만
	}

	private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
		return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), message)); // 상태와 본문 조립
	}
}
