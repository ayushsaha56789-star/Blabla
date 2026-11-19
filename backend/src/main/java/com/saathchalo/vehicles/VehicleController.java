package com.saathchalo.vehicles;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.vehicles.VehicleDtos.VehicleRequest;
import com.saathchalo.vehicles.VehicleDtos.VehicleView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "My cars": the cars a member offers rides in. */
@RestController
@RequestMapping("/api/vehicles")
@RequiresFeature("rides.publish")
public class VehicleController {

	private final VehicleService vehicles;

	public VehicleController(VehicleService vehicles) {
		this.vehicles = vehicles;
	}

	@GetMapping
	public List<VehicleView> mine() {
		return vehicles.mine(CurrentUser.require());
	}

	@PostMapping
	public VehicleView add(@Valid @RequestBody VehicleRequest request) {
		return vehicles.add(CurrentUser.require(), request);
	}

	@PutMapping("/{id}")
	public VehicleView update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
		return vehicles.update(CurrentUser.require(), id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> remove(@PathVariable UUID id) {
		vehicles.remove(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}
}
