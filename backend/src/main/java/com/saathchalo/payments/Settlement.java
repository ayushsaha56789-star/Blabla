package com.saathchalo.payments;

import com.saathchalo.payments.internal.PaymentRepository;
import com.saathchalo.payments.internal.RefundRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: gives back credits held by checkouts nobody finished, settles or refunds paid bookings that are over,
 * and sends pending refunds. Each step works on one payment at a time, so one problem never blocks the rest, and
 * anything missed is picked up on the next run.
 */
@Component
public class Settlement {

	private static final Logger log = LoggerFactory.getLogger(Settlement.class);

	/** A little slack after a hold ends, so a payment that's just arriving isn't given up on. */
	static final Duration GRACE = Duration.ofMinutes(2);

	private final PaymentService service;

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final Clock clock;

	public Settlement(PaymentService service, PaymentRepository payments, RefundRepository refunds, Clock clock) {
		this.service = service;
		this.payments = payments;
		this.refunds = refunds;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
	public void run() {
		for (UUID id : payments.findUnpaidBefore(clock.instant().minus(GRACE), PageRequest.of(0, 200))) {
			safely("expire", id, () -> service.expireIfUnpaid(id));
		}
		for (UUID id : payments.findOpenPaid(PageRequest.of(0, 500))) {
			safely("settle", id, () -> service.settle(id));
		}
		for (UUID id : refunds.findToSend(PageRequest.of(0, 100))) {
			safely("refund", id, () -> service.sendRefund(id));
		}
	}

	private static void safely(String step, UUID id, Runnable work) {
		try {
			work.run();
		}
		catch (RuntimeException e) {
			log.error("Payments: couldn't {} {}; will try again next minute", step, id, e);
		}
	}
}
