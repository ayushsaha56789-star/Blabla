package com.saathchalo.safety;

import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.PersonalData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Emergency contacts, trip-sharing links and SOS alerts. On erasure, contacts and links are deleted. SOS alerts stay
 * as safety records (their location is cleared after 90 days, as for everyone).
 */
@Component
class SafetyPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final FieldEncryptor encryptor;

	SafetyPersonalData(JdbcTemplate jdbc, FieldEncryptor encryptor) {
		this.jdbc = jdbc;
		this.encryptor = encryptor;
	}

	@Override
	public String section() {
		return "safety";
	}

	@Override
	public int order() {
		return 60;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("emergencyContacts", PersonalData.rows(jdbc.queryForList(
				"SELECT name, phone_enc, created_at AS added FROM emergency_contacts WHERE user_id = ? ORDER BY created_at", memberId))
			.stream()
			.map(row -> {
				row.put("phone", encryptor.decrypt((String) row.remove("phone_enc")));
				return (Map<String, Object>) row;
			})
			.toList());
		out.put("tripShares", PersonalData.rows(jdbc.queryForList("""
				SELECT ride_id AS "rideId", created_at AS created, expires_at AS expires, revoked_at AS stopped,
				       live_lat AS "lastLat", live_lng AS "lastLng", live_at AS "lastLocationAt"
				FROM trip_shares WHERE user_id = ? ORDER BY created_at DESC""", memberId)));
		out.put("sosAlerts", PersonalData.rows(jdbc.queryForList("""
				SELECT ride_id AS "rideId", created_at AS raised, lat, lng, status, contacts_notified AS "contactsTexted",
				       resolved_at AS resolved
				FROM sos_alerts WHERE user_id = ? ORDER BY created_at DESC""", memberId)));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM emergency_contacts WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM trip_shares WHERE user_id = ?", memberId);
	}
}
