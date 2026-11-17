package com.finshot.remittance.error;

/** Body of every non-2xx response: {"error": "...", "message": "..."} */
public record ErrorResponse(String error, String message) {
}
