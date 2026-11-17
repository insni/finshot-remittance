package com.finshot.remittance.partner;

import org.springframework.stereotype.Component;

/** Fake partner. No real service is called; success or failure depends only on partnerRef (no randomness). */
@Component
public class PartnerClient {

	static final long MIN_LATENCY_MS = 200; // minimum time of one attempt

	/** True if the partner accepts the request. Accepted is not completed. Spec: the attempt fails when {@code abs(partnerRef.hashCode()) % 10 < 3}. */
	public boolean submit(String partnerRef) {
		sleepLatency(); // takes at least 200ms
		return Math.abs((long) partnerRef.hashCode()) % 10 >= 3; // cast to long so abs is correct for Integer.MIN_VALUE (int abs would stay negative)
	}

	private static void sleepLatency() {
		try {
			Thread.sleep(MIN_LATENCY_MS); // simulate partner latency
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupt flag
			throw new IllegalStateException("interrupted during partner call", e); // do not swallow the interrupt
		}
	}
}
