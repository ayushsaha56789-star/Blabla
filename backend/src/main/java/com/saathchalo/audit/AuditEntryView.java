package com.saathchalo.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryView(long id, Instant occurredAt, UUID actorId, String actorLabel, String action,
		String targetType, String targetId, String summary, String details, String ip, String requestId) {
}
