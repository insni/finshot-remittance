package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.finshot.remittance.partner.PartnerClient;

/** S9: partnerRef가 파트너 실패를 유발하는 송금을 생성. 재시도 로그와 최종 상태를 표시. */
@DisplayName("S9 partnerRef가 파트너 실패를 유발하는 송금 생성")
class S9PartnerFailTest extends ScenarioTest {

	@Autowired
	PartnerClient client; // 파트너 규칙 직접 확인용

	@Test
	void S9_파트너가_3번_모두_거절하면_FAILED와_시도_기록_3건() {
		var body = sendUntil("FAILED", 60); // 실패하는 송금을 찾을 때까지 반복
		assertEquals("FAILED", body.get("status").asText()); // 최종 상태
		assertEquals("partner rejected 3 attempts", body.get("failureReason").asText()); // 실패 사유
		assertEquals(3, body.get("attempts").size()); // 시도 기록 3건
		for (int i = 0; i < 3; i++) {
			assertEquals(i + 1, body.get("attempts").get(i).get("attemptNo").asInt()); // 1,2,3 순서
			assertEquals("FAILED", body.get("attempts").get(i).get("result").asText()); // 모두 거절
		}
		assertEquals("P-" + body.get("transferId").asText(), body.get("partnerRef").asText()); // 참조값 규칙
	}

	@Test
	void S9_파트너_규칙은_같은_참조값에_항상_같은_결과이고_시도는_200ms_이상() {
		long begin = System.nanoTime(); // 시작 시각
		assertFalse(client.submit("d")); // "d".hashCode()=100 -> 0 < 3 이라 실패
		assertFalse(client.submit("d")); // 다시 불러도 같은 결과 (난수 없음)
		assertTrue(client.submit("a")); // "a".hashCode()=97 -> 7 이라 접수
		long elapsedMs = (System.nanoTime() - begin) / 1_000_000; // 세 번 호출에 걸린 시간
		assertTrue(elapsedMs >= 600, "한 번에 최소 200ms: " + elapsedMs); // 3회 x 200ms
	}
}
