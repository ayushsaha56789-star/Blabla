package com.saathchalo.rewards;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every hour: invited friends who have completed a ride with someone else earn both sides their credits. */
@Component
public class RewardHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(RewardHousekeeping.class);

	private final RewardService rewards;

	public RewardHousekeeping(RewardService rewards) {
		this.rewards = rewards;
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 900_000)
	public void payReferrals() {
		for (UUID[] r : rewards.pendingReferrals()) {
			try {
				rewards.settle(r[0], r[1]);
			}
			catch (RuntimeException e) {
				log.warn("Could not settle the referral of member {}", r[0], e);
			}
		}
	}
}
