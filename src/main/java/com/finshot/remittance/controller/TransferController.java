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

/** HTTP 입출력만 담당. 판단은 서비스, 오류 변환은 GlobalExceptionHandler. */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private final TransferService service; // 업무 흐름 담당
	private final PartnerDispatchService dispatch; // 파트너 전송 담당

	TransferController(TransferService service, PartnerDispatchService dispatch) {
		this.service = service; // 주입값 보관
		this.dispatch = dispatch; // 주입값 보관
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // 재시도(같은 키·같은 내용)도 201
	TransferResponse create(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, // 없으면 null로 받아 검증기가 400 처리
			@RequestBody CreateTransferRequest request) {
		return TransferResponse.from(service.create(idempotencyKey, request)); // 생성(또는 재생) 결과
	}

	@GetMapping("/{transferId}")
	TransferResponse get(@PathVariable String transferId) {
		return TransferResponse.from(service.get(transferId)); // 단건 조회
	}

	@PostMapping("/{transferId}/cancel")
	TransferResponse cancel(@PathVariable String transferId) {
		return TransferResponse.from(service.cancel(transferId)); // 취소 후 갱신본
	}

	@PostMapping("/{transferId}/send")
	TransferResponse send(@PathVariable String transferId) {
		return dispatch.send(transferId); // 동기 실행: 접수면 SENDING, 3회 실패면 FAILED (시도 기록 포함)
	}
}
