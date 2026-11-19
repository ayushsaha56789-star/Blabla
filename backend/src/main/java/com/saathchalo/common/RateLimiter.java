package com.saathchalo.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sliding-window counters kept in memory. Fine for one server; swap for Redis when SaathChalo runs on several.
 */
@Component
public class RateLimiter {

	private static final long FORGET_AFTER_MS = Duration.ofHours(2).toMillis();

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	private final Clock clock;

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Counts one hit and says whether it is within {@code limit} hits per {@code window}. */
	public boolean tryAcquire(String key, int limit, Duration window) {
		long now = clock.millis();
		Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (queue) {
			prune(queue, now - window.toMillis());
			if (queue.size() >= limit) {
				return false;
			}
			queue.addLast(now);
			return true;
		}
	}

	/** How many hits were recorded for {@code key} within {@code window}, without adding one. */
	public int count(String key, Duration window) {
		Deque<Long> queue = hits.get(key);
		if (queue == null) {
			return 0;
		}
		synchronized (queue) {
			prune(queue, clock.millis() - window.toMillis());
			return queue.size();
		}
	}

	public void record(String key) {
		Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (queue) {
			queue.addLast(clock.millis());
		}
	}

	public void reset(String key) {
		hits.remove(key);
	}

	@Scheduled(fixedDelay = 600_000)
	void forgetOldKeys() {
		long cutoff = clock.millis() - FORGET_AFTER_MS;
		hits.entrySet().removeIf(e -> {
			synchronized (e.getValue()) {
				Long last = e.getValue().peekLast();
				return last == null || last < cutoff;
			}
		});
	}

	private static void prune(Deque<Long> queue, long cutoff) {
		while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
			queue.pollFirst();
		}
	}
}
