package com.finshot.remittance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.service.PartnerDispatchService;
import com.finshot.remittance.service.TransferService;

/** Handles HTTP input/output only. Decisions are in the service, error mapping in GlobalExceptionHandler. */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private final TransferService service; // business flow
	private final PartnerDispatchService dispatch; // partner dispatch

	TransferController(TransferService service, PartnerDispatchService dispatch) {
		this.service = service;
		this.dispatch = dispatch;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // a retry (same key, same content) is also 201
	TransferResponse create(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, // missing header arrives as null; the validator turns it into 400
			@RequestBody CreateTransferRequest request) {
		return TransferResponse.from(service.create(idempotencyKey, request)); // created (or replayed) transfer
	}

	@GetMapping("/{transferId}")
	TransferResponse get(@PathVariable String transferId) {
		return TransferResponse.from(service.get(transferId));
	}

	@PostMapping("/{transferId}/cancel")
	TransferResponse cancel(@PathVariable String transferId) {
		return TransferResponse.from(service.cancel(transferId)); // updated transfer after cancel
	}

	@PostMapping("/{transferId}/send")
	TransferResponse send(@PathVariable String transferId) {
		return dispatch.send(transferId); // runs synchronously: SENDING if accepted, FAILED after 3 failures (attempts included)
	}
}
