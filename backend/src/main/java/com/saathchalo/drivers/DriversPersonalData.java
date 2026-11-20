package com.saathchalo.drivers;

import com.saathchalo.common.PersonalData;
import com.saathchalo.files.DocumentVault;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Driver partners and hired trips. On erasure the driver profile goes (with its areas, time off and police
 * certificate); trips stay as part of the other side's history, without the member's address, messages or comments.
 */
@Component
class DriversPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final DocumentVault vault;

	DriversPersonalData(JdbcTemplate jdbc, DocumentVault vault) {
		this.jdbc = jdbc;
		this.vault = vault;
	}

	@Override
	public String section() {
		return "drivers";
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		List<Map<String, Object>> profile = PersonalData.rows(jdbc.queryForList("""
				SELECT status, licence_class AS "licenceClass", licence_valid_until AS "licenceValidUntil",
				       experience_years AS "experienceYears", languages, manual, automatic, outstation, hour_price AS "hourPrice",
				       day_price AS "dayPrice", outstation_price AS "outstationPrice", allowance, night_charge AS "nightCharge",
				       about, police_certificate IS NOT NULL AS "policeCertificateAdded", taking_bookings AS "takingBookings",
				       created_at AS "signedUp"
				FROM driver_profiles WHERE user_id = ?""", memberId));
		out.put("driverProfile", profile.isEmpty() ? null : profile.getFirst());
		out.put("areas", PersonalData.rows(jdbc.queryForList(
				"SELECT place_name AS town, radius_km AS \"radiusKm\" FROM driver_areas WHERE driver_id = ? ORDER BY created_at", memberId)));
		out.put("tripsHired", PersonalData.rows(jdbc.queryForList("""
				SELECT id, kind, starts_at AS "from", ends_at AS "to", place_name AS town, pickup_address AS "pickupAddress",
				       destination_name AS destination, car, fare, status, message, created_at AS booked
				FROM driver_trips WHERE customer_id = ? ORDER BY starts_at DESC""", memberId)));
		out.put("tripsDriven", PersonalData.rows(jdbc.queryForList("""
				SELECT id, kind, starts_at AS "from", ends_at AS "to", place_name AS town, fare, status, extra_units AS "extraUnits",
				       created_at AS booked
				FROM driver_trips WHERE driver_id = ? ORDER BY starts_at DESC""", memberId)));
		out.put("ratingsGiven", PersonalData.rows(jdbc.queryForList("""
				SELECT trip_id AS trip, stars, comment, created_at AS "at" FROM driver_reviews WHERE author_id = ? ORDER BY created_at""",
				memberId)));
		return out;
	}

	@Override
	public String leavingBlocker(UUID memberId) {
		Long active = jdbc.queryForObject("""
				SELECT count(*) FROM driver_trips WHERE (driver_id = ? OR customer_id = ?)
				    AND status IN ('ACCEPTED', 'PAID', 'ON_THE_WAY', 'STARTED')""", Long.class, memberId, memberId);
		return active != null && active > 0
				? "You have driver trips coming up. Finish or cancel them first, then delete your account." : null;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.queryForList("SELECT police_certificate FROM driver_profiles WHERE user_id = ? AND police_certificate IS NOT NULL",
				String.class, memberId).forEach(vault::deleteQuietly);
		jdbc.update("DELETE FROM driver_profiles WHERE user_id = ?", memberId);
		jdbc.update("UPDATE driver_trips SET pickup_address = '', message = NULL WHERE customer_id = ?", memberId);
		jdbc.update("UPDATE driver_trips SET cancel_reason = NULL WHERE driver_id = ? AND cancelled_by = 'DRIVER'", memberId);
		jdbc.update("UPDATE driver_reviews SET comment = NULL WHERE author_id = ?", memberId);
	}
}
