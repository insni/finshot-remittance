package com.finshot.remittance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.finshot.remittance.domain.Transfer;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.error.ConflictException;
import com.finshot.remittance.error.NotFoundException;
import com.finshot.remittance.partner.PartnerAttempt;
import com.finshot.remittance.partner.PartnerClient;
import com.finshot.remittance.repository.PartnerRepository;
import com.finshot.remittance.repository.TransferRepository;

/**
 * Flow that sends a transfer to the partner. The SENDING transition and partner_ref are saved before the partner call; no DB transaction or lock is held during the call.
 * Acceptance is not completion: if accepted, the transfer stays SENDING and waits for the callback.
 */
@Service
public class PartnerDispatchService {

	private static final Logger log = LoggerFactory.getLogger(PartnerDispatchService.class); // for attempt logs
	private static final long[] BACKOFF_MS = {100, 200}; // waits between attempts 1->2 and 2->3. length + 1 = total attempts
	private static final String FAILURE_REASON = "partner rejected " + (BACKOFF_MS.length + 1) + " attempts"; // FAILED reason

	private final TransferRepository transfers; // transfer SQL
	private final PartnerRepository partners; // attempt SQL
	private final PartnerClient client; // fake partner

	PartnerDispatchService(TransferRepository transfers, PartnerRepository partners, PartnerClient client) {
		this.transfers = transfers;
		this.partners = partners;
		this.client = client;
	}

	/** Runs synchronously: returns the transfer, SENDING (accepted) or FAILED (3 failures), with its attempts. */
	public TransferResponse send(String transferId) {
		String partnerRef = "P-" + transferId; // fixed per transfer: a retry sends the same value, so the partner sees no duplicate
		if (transfers.startSending(transferId, partnerRef, TransferService.now()) == 0) { // 1) one conditional UPDATE (atomic by itself)
			Transfer current = transfers.findById(transferId).orElseThrow(() -> new NotFoundException("transfer not found: " + transferId)); // 404 if missing
			throw new ConflictException("cannot send a transfer in status " + current.status()); // 409 if it exists
		}
		boolean accepted = callPartner(transferId, partnerRef); // 2) call up to 3 times, outside any transaction
		if (!accepted) {
			transfers.fail(transferId, FAILURE_REASON, TransferService.now()); // 3) FAILED only if still SENDING; 0 rows means do not overwrite
		}
		Transfer latest = transfers.findById(transferId).orElseThrow(); // a callback may have arrived meanwhile, so re-read the current value
		return TransferResponse.from(latest, partners.findAttempts(transferId)); // include attempts in the response
	}

	/** Calls until accepted or attempts run out. Each attempt is saved to the DB and logged. */
	private boolean callPartner(String transferId, String partnerRef) {
		for (int attemptNo = 1; attemptNo <= BACKOFF_MS.length + 1; attemptNo++) { // 3 attempts in total
			boolean accepted = client.submit(partnerRef); // call the partner
			String result = accepted ? "ACCEPTED" : "FAILED"; // result text
			partners.insertAttempt(transferId, new PartnerAttempt(attemptNo, partnerRef, result, TransferService.now())); // save the attempt
			log.info("partner call attempt={} partnerRef={} result={}", attemptNo, partnerRef, result); // log attempt number, partnerRef, result
			if (accepted) {
				return true; // done once accepted
			}
			if (attemptNo <= BACKOFF_MS.length) {
				backoff(BACKOFF_MS[attemptNo - 1]); // wait before the next attempt
			}
		}
		return false; // all 3 attempts failed
	}

	private static void backoff(long millis) {
		try {
			Thread.sleep(millis); // 100ms, then 200ms
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupt flag
			throw new IllegalStateException("interrupted while waiting to retry the partner call", e); // do not swallow the interrupt
		}
	}
}
