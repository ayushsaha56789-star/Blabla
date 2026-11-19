package com.saathchalo.vehicles;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.vehicles.VehicleDtos.VehicleRequest;
import com.saathchalo.vehicles.VehicleDtos.VehicleView;
import com.saathchalo.vehicles.internal.Vehicle;
import com.saathchalo.vehicles.internal.VehicleRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members' cars. The registration number is stored encrypted: the owner sees it, passengers see it once their seat is
 * confirmed (so they can spot the right car), and everyone else sees only the make, model and colour.
 */
@Service
public class VehicleService {

	static final int MAX_VEHICLES = 10;

	private final VehicleRepository vehicles;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	public VehicleService(VehicleRepository vehicles, FieldEncryptor encryptor, Clock clock) {
		this.vehicles = vehicles;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<VehicleView> mine(UUID ownerId) {
		return vehicles.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtAsc(ownerId).stream().map(this::view).toList();
	}

	@Transactional
	public VehicleView add(UUID ownerId, VehicleRequest req) {
		if (vehicles.countByOwnerIdAndDeletedAtIsNull(ownerId) >= MAX_VEHICLES) {
			throw ApiException.conflict("too_many_vehicles",
					"You can save up to " + MAX_VEHICLES + " cars. Remove one you no longer use first.");
		}
		Vehicle v = new Vehicle(ownerId, clock.instant());
		apply(v, req);
		return view(vehicles.save(v));
	}

	@Transactional
	public VehicleView update(UUID ownerId, UUID vehicleId, VehicleRequest req) {
		Vehicle v = owned(ownerId, vehicleId);
		apply(v, req);
		return view(v);
	}

	/** Removes a car from the member's list. Rides already offered with it keep showing it. */
	@Transactional
	public void remove(UUID ownerId, UUID vehicleId) {
		owned(ownerId, vehicleId).delete(clock.instant());
	}

	/** One of the member's current cars, if it is theirs. */
	@Transactional(readOnly = true)
	public Optional<VehicleInfo> activeOf(UUID ownerId, UUID vehicleId) {
		return vehicles.findById(vehicleId).filter(v -> v.getOwnerId().equals(ownerId) && v.isActive()).map(VehicleService::info);
	}

	/** Cars by id, including removed ones (rides keep their car). */
	@Transactional(readOnly = true)
	public Map<UUID, VehicleInfo> infos(Collection<UUID> ids) {
		Map<UUID, VehicleInfo> out = new LinkedHashMap<>();
		vehicles.findAllById(ids.stream().distinct().toList()).forEach(v -> out.put(v.getId(), info(v)));
		return out;
	}

	/** The full registration number, e.g. "MH 12 AB 1234". The caller checks that the viewer may see it. */
	@Transactional(readOnly = true)
	public Optional<String> plate(UUID vehicleId) {
		return vehicles.findById(vehicleId).map(v -> Plates.format(encryptor.decrypt(v.getPlateEnc())));
	}

	private Vehicle owned(UUID ownerId, UUID vehicleId) {
		return vehicles.findById(vehicleId)
			.filter(v -> v.getOwnerId().equals(ownerId) && v.isActive())
			.orElseThrow(() -> ApiException.notFound("vehicle_not_found", "We couldn't find that car in your list."));
	}

	private void apply(Vehicle v, VehicleRequest req) {
		String plate = Plates.normalize(req.plate());
		v.update(clean(req.make()), clean(req.model()), blankToNull(req.color()), req.seats(), encryptor.encrypt(plate),
				Plates.hint(plate));
	}

	private VehicleView view(Vehicle v) {
		return new VehicleView(v.getId(), v.getMake(), v.getModel(), v.getColor(), v.getSeats(),
				Plates.format(encryptor.decrypt(v.getPlateEnc())), v.getCreatedAt());
	}

	private static VehicleInfo info(Vehicle v) {
		return new VehicleInfo(v.getId(), v.getOwnerId(), v.getMake(), v.getModel(), v.getColor(), v.getSeats(),
				v.getPlateHint(), v.isActive());
	}

	private static String clean(String s) {
		return s.strip().replaceAll("\\s+", " ");
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : clean(s);
	}
}
