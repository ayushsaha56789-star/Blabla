package com.saathchalo.vehicles;

import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.PersonalData;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The member's cars, with their full number plates. */
@Component
class VehiclePersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final FieldEncryptor encryptor;

	VehiclePersonalData(JdbcTemplate jdbc, FieldEncryptor encryptor) {
		this.jdbc = jdbc;
		this.encryptor = encryptor;
	}

	@Override
	public String section() {
		return "cars";
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public Object export(UUID memberId) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT make, model, color AS colour, seats, plate_enc, created_at AS added, deleted_at AS removed
				FROM vehicles WHERE owner_id = ? ORDER BY created_at""", memberId))
			.stream()
			.map(row -> {
				String plate = encryptor.decrypt((String) row.remove("plate_enc"));
				row.put("numberPlate", Plates.format(plate));
				return (Map<String, Object>) row;
			})
			.toList();
	}

	/** Past rides still point at the car (make and model stay), but the number plate goes. */
	@Override
	public void erase(UUID memberId) {
		jdbc.update("UPDATE vehicles SET plate_enc = ?, plate_hint = '', deleted_at = COALESCE(deleted_at, now()) WHERE owner_id = ?",
				encryptor.encrypt(""), memberId);
	}
}
