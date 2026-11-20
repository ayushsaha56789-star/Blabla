package com.saathchalo.rentals;

import com.saathchalo.common.PersonalData;
import com.saathchalo.files.CarPhotoStorage;
import com.saathchalo.files.DocumentVault;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rental listings and bookings. On erasure the member's listings are removed (with their photos and documents);
 * bookings stay as part of the other side's history, without the member's notes or photos.
 */
@Component
class RentalsPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final DocumentVault vault;

	private final CarPhotoStorage photos;

	private final Clock clock;

	RentalsPersonalData(JdbcTemplate jdbc, DocumentVault vault, CarPhotoStorage photos, Clock clock) {
		this.jdbc = jdbc;
		this.vault = vault;
		this.photos = photos;
		this.clock = clock;
	}

	@Override
	public String section() {
		return "rentals";
	}

	@Override
	public int order() {
		return 32;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("listings", PersonalData.rows(jdbc.queryForList("""
				SELECT place_name AS "town", area, pickup_address AS "pickupAddress", self_drive AS "selfDrive",
				       with_driver AS "withDriver", day_price AS "dayPrice", hour_price AS "hourPrice", deposit, status,
				       created_at AS created
				FROM rental_listings WHERE owner_id = ? ORDER BY created_at DESC""", memberId)));
		out.put("rentedByYou", PersonalData.rows(jdbc.queryForList("""
				SELECT mode, starts_at AS "from", ends_at AS "to", rent, deposit, status, extra_charges AS "extraCharges",
				       message, dispute_note AS "disputeNote", created_at AS booked
				FROM rental_bookings WHERE renter_id = ? ORDER BY starts_at DESC""", memberId)));
		out.put("yourCarRented", PersonalData.rows(jdbc.queryForList("""
				SELECT mode, starts_at AS "from", ends_at AS "to", rent, deposit, status, extra_charges AS "extraCharges",
				       extra_note AS "chargesNote", created_at AS booked
				FROM rental_bookings WHERE owner_id = ? ORDER BY starts_at DESC""", memberId)));
		return out;
	}

	@Override
	public String leavingBlocker(UUID memberId) {
		Long active = jdbc.queryForObject("""
				SELECT count(*) FROM rental_bookings WHERE (renter_id = ? OR owner_id = ?)
				    AND status IN ('ACCEPTED', 'PAID', 'PICKED_UP', 'RETURNED', 'DISPUTED')""", Long.class, memberId, memberId);
		return active != null && active > 0
				? "You have car rentals that aren't finished yet. Finish or cancel them first, then delete your account." : null;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.queryForList("""
				SELECT d.file_name FROM rental_documents d JOIN rental_listings l ON l.id = d.listing_id WHERE l.owner_id = ?""",
				String.class, memberId).forEach(vault::deleteQuietly);
		jdbc.queryForList("SELECT p.path FROM rental_photos p JOIN rental_listings l ON l.id = p.listing_id WHERE l.owner_id = ?",
				String.class, memberId).forEach(photos::deleteQuietly);
		jdbc.update("DELETE FROM rental_documents WHERE listing_id IN (SELECT id FROM rental_listings WHERE owner_id = ?)", memberId);
		jdbc.update("DELETE FROM rental_photos WHERE listing_id IN (SELECT id FROM rental_listings WHERE owner_id = ?)", memberId);
		jdbc.update("UPDATE rental_listings SET status = 'REMOVED', pickup_address = NULL, description = NULL, rules = NULL, "
				+ "updated_at = ? WHERE owner_id = ?", java.sql.Timestamp.from(clock.instant()), memberId);
		// Their own check photos and words go; the other side's records stay.
		jdbc.queryForList("""
				SELECT c.photos FROM rental_checks c JOIN rental_bookings b ON b.id = c.booking_id
				WHERE (c.by_role = 'RENTER' AND b.renter_id = ?) OR (c.by_role = 'OWNER' AND b.owner_id = ?)""",
				String.class, memberId, memberId)
			.stream().flatMap(p -> Arrays.stream(p.split(","))).filter(f -> !f.isBlank()).forEach(vault::deleteQuietly);
		jdbc.update("""
				UPDATE rental_checks c SET photos = '', notes = NULL FROM rental_bookings b
				WHERE b.id = c.booking_id AND ((c.by_role = 'RENTER' AND b.renter_id = ?) OR (c.by_role = 'OWNER' AND b.owner_id = ?))""",
				memberId, memberId);
		jdbc.update("UPDATE rental_bookings SET message = NULL, dispute_note = NULL WHERE renter_id = ?", memberId);
		jdbc.update("UPDATE rental_bookings SET extra_note = NULL WHERE owner_id = ?", memberId);
	}
}
