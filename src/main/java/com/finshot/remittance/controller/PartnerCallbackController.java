package com.finshot.remittance.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.service.PartnerCallbackService;

/** HTTP input/output for partner callbacks only. */
@RestController
@RequestMapping("/api/callbacks/partner")
public class PartnerCallbackController {

	private final PartnerCallbackService service; // callback handling

	PartnerCallbackController(PartnerCallbackService service) {
		this.service = service;
	}

	@PostMapping
	TransferResponse receive(@RequestBody PartnerCallbackRequest request) {
		return TransferResponse.from(service.receive(request)); // first and duplicate deliveries both return 200 + current transfer
	}
}
