package com.finshot.remittance.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

/** S1: C001, 금액 500000으로 송금 생성. */
@DisplayName("S1 C001, 금액 500000으로 송금 생성")
class S1CreateTest extends ScenarioTest {

	@Test
	void S1_500000_생성은_201과_계산된_금액을_돌려준다() {
		var res = create("C001", "500000", newKey()); // 과제 예시 그대로
		var body = res.getBody(); // 응답 본문
		assertEquals(HttpStatus.CREATED, res.getStatusCode()); // 201
		assertEquals("REQUESTED", body.get("status").asText()); // 처음 상태
		assertEquals("500000", body.get("sendAmount").asText()); // 보낸 금액
		assertEquals("5000", body.get("fee").asText()); // 수수료 1%
		assertEquals("505000", body.get("totalDebit").asText()); // 금액 + 수수료
		assertEquals("20600.00", body.get("receiveAmount").asText()); // 소수 2자리 유지
		assertTrue(body.hasNonNull("transferId")); // 송금 ID 있음
		assertTrue(body.hasNonNull("createdAt")); // 생성 시각 있음
	}

	@ParameterizedTest(name = "S1 금액 {0} -> 수수료 {1}")
	@CsvSource({
			"10000, 3000, 13000, 412.00", // 1%=100이지만 최소 3000
			"250050, 3000, 253050, 10302.06", // 1%=2500.5 -> 반올림해도 3000 미만이라 최소 3000
			"350050, 3501, 353551, 14422.06", // 1%=3500.5 -> HALF_UP으로 3501
			"10013, 3000, 13013, 412.54" // 받는 돈 412.5356 -> HALF_UP 412.54
	})
	void S1_수수료_최소값과_반올림_경계(String sendAmount, String fee, String totalDebit, String receiveAmount) {
		var res = create("C001", sendAmount, newKey()); // 경계 금액으로 생성
		var body = res.getBody(); // 응답 본문
		assertEquals(HttpStatus.CREATED, res.getStatusCode()); // 201
		assertEquals(fee, body.get("fee").asText()); // 수수료
		assertEquals(totalDebit, body.get("totalDebit").asText()); // 총 출금액
		assertEquals(receiveAmount, body.get("receiveAmount").asText()); // 받는 금액
	}
}
