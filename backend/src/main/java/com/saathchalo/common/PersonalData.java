package com.saathchalo.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implemented by every module that keeps personal data about members, so the privacy centre can give members a copy
 * of their data and erase it when they delete their account. Each module handles only its own tables.
 */
public interface PersonalData {

	/** The section's name in the downloaded file, e.g. "rides". */
	String section();

	/** Sections are exported in this order, and erased in the reverse order (so the profile goes last). */
	default int order() {
		return 100;
	}

	/** What this module holds about the member, as JSON-friendly values: maps, lists, strings, numbers, booleans. */
	Object export(UUID memberId);

	/** Removes or anonymises the member's data. Runs inside the erasure's transaction. */
	void erase(UUID memberId);

	/** A friendly reason the member can't delete their account yet (say, money still owed to them), or null. */
	default String leavingBlocker(UUID memberId) {
		return null;
	}

	/** Makes JDBC rows JSON-friendly: times as ISO-8601 text, ids as text, column order kept. */
	static List<Map<String, Object>> rows(List<Map<String, Object>> raw) {
		return raw.stream().map(PersonalData::row).toList();
	}

	static Map<String, Object> row(Map<String, Object> raw) {
		Map<String, Object> clean = new LinkedHashMap<>();
		raw.forEach((key, value) -> clean.put(key, value(value)));
		return clean;
	}

	private static Object value(Object value) {
		if (value instanceof Timestamp t) {
			return t.toInstant().toString();
		}
		if (value instanceof java.sql.Date d) {
			return d.toLocalDate().toString();
		}
		if (value instanceof Instant || value instanceof UUID) {
			return value.toString();
		}
		return value;
	}
}
