package com.finshot.remittance.transfer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 파트너 콜백 HTTP 입출력만 담당. */
@RestController
@RequestMapping("/api/callbacks/partner")
class PartnerCallbackController {

	private final PartnerCallbackService service; // 콜백 처리 담당

	PartnerCallbackController(PartnerCallbackService service) {
		this.service = service; // 주입값 보관
	}

	@PostMapping
	TransferResponse receive(@RequestBody PartnerCallbackRequest request) {
		return TransferResponse.from(service.receive(request)); // 첫 전달·중복 전달 모두 200 + 현재 송금
	}
}
