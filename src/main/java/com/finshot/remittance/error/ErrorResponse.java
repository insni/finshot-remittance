package com.finshot.remittance.error;

/** 모든 비 2xx 응답 본문: {"error": "...", "message": "..."} */
public record ErrorResponse(String error, String message) {
}
