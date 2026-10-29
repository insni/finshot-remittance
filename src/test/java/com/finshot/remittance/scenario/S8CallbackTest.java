package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S8: 같은 콜백(같은 eventId)을 두 번 전송. 두 응답과 최종 상태를 표시. */
@DisplayName("S8 같은 콜백(같은 eventId)을 두 번 전송")
class S8CallbackTest extends ScenarioTest {

	private int events(String partnerRef) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM callback_events WHERE partner_ref = ?", Integer.class,
				partnerRef); // 그 참조값의 콜백 기록 수
	}

	@Test
	void S8_같은_eventId_콜백_두_번은_둘_다_200이고_COMPLETED는_한_번만() {
		String ref = sendUntil("SENDING", 30).get("partnerRef").asText(); // SENDING 송금의 파트너 참조값
		String eventId = "evt_" + newKey(); // 같은 이벤트 ID
		var first = callback(ref, eventId); // 첫 전달
		var second = callback(ref, eventId); // 중복 전달
		assertEquals(HttpStatus.OK, first.getStatusCode()); // 첫 전달 200
		assertEquals(HttpStatus.OK, second.getStatusCode()); // 중복도 200
		assertEquals("COMPLETED", first.getBody().get("status").asText()); // 첫 응답 상태
		assertEquals("COMPLETED", second.getBody().get("status").asText()); // 둘째 응답도 같은 상태
		assertEquals(1, events(ref)); // 이벤트 기록은 한 행
	}

	@Test
	void S8_이미_완료된_송금에_새_eventId_콜백은_409() {
		String ref = sendUntil("SENDING", 30).get("partnerRef").asText(); // SENDING 송금의 파트너 참조값
		assertEquals(HttpStatus.OK, callback(ref, "evt_" + newKey()).getStatusCode()); // 먼저 완료시킴
		var late = callback(ref, "evt_" + newKey()); // 다른 eventId로 또 콜백
		assertEquals(HttpStatus.CONFLICT, late.getStatusCode()); // SENDING이 아니라 409
		assertEquals(1, events(ref)); // 실패한 이벤트는 롤백되어 한 행 그대로
	}
}
