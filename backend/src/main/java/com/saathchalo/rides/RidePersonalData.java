package com.saathchalo.rides;

import com.saathchalo.common.PersonalData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rides the member offered and seats they booked. On erasure the rows stay (they are part of other members' trip
 * history, and the member's name is gone from the profile) but the member's own words are removed.
 */
@Component
class RidePersonalData implements PersonalData {

	private static final String ROUTE =
			"(SELECT s.name FROM ride_stops s WHERE s.ride_id = r.id ORDER BY s.position LIMIT 1) AS \"from\", "
			+ "(SELECT s.name FROM ride_stops s WHERE s.ride_id = r.id ORDER BY s.position DESC LIMIT 1) AS \"to\" ";

	private final JdbcTemplate jdbc;

	RidePersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "rides";
	}

	@Override
	public int order() {
		return 30;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("offered", PersonalData.rows(jdbc.queryForList("SELECT " + ROUTE + """
				, r.departure_at AS departure, r.status, r.seats_total AS seats, r.price_per_seat AS "pricePerSeat",
				  r.distance_km AS "distanceKm", r.ladies_only AS "ladiesOnly", r.notes, r.cancel_reason AS "cancelReason",
				  r.created_at AS created
				FROM rides r WHERE r.driver_id = ? ORDER BY r.departure_at DESC""", memberId)));
		out.put("booked", PersonalData.rows(jdbc.queryForList("""
				SELECT f.name AS "from", t.name AS "to", r.departure_at AS departure, b.seats, b.price_per_seat AS "pricePerSeat",
				       b.status, b.message AS "messageToDriver", b.created_at AS requested, b.cancelled_at AS cancelled
				FROM bookings b
				JOIN rides r ON r.id = b.ride_id
				JOIN ride_stops f ON f.ride_id = b.ride_id AND f.position = b.from_position
				JOIN ride_stops t ON t.ride_id = b.ride_id AND t.position = b.to_position
				WHERE b.passenger_id = ? ORDER BY r.departure_at DESC""", memberId)));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("UPDATE bookings SET message = NULL WHERE passenger_id = ?", memberId);
		jdbc.update("UPDATE rides SET notes = NULL, cancel_reason = CASE WHEN cancelled_by = 'DRIVER' THEN NULL ELSE cancel_reason END WHERE driver_id = ?",
				memberId);
	}
}
