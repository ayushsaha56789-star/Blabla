package com.saathchalo.vehicles;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class VehicleDtos {

	private VehicleDtos() {
	}

	private static final String CAR_NAME = "^[\\p{L}\\p{N} .&'+/-]*$";

	private static final String COLOUR = "^[\\p{L} -]*$";

	public record VehicleRequest(
			@NotBlank(message = "Please enter the make, e.g. Maruti Suzuki")
			@Size(max = 40, message = "Use up to 40 characters")
			@Pattern(regexp = CAR_NAME, message = "Please use letters and numbers only") String make,
			@NotBlank(message = "Please enter the model, e.g. Swift")
			@Size(max = 40, message = "Use up to 40 characters")
			@Pattern(regexp = CAR_NAME, message = "Please use letters and numbers only") String model,
			@Size(max = 30, message = "Use up to 30 characters")
			@Pattern(regexp = COLOUR, message = "Please use letters only") String color,
			@NotNull(message = "How many passengers can you take?")
			@Min(value = 1, message = "At least 1 seat") @Max(value = 8, message = "At most 8 seats") Integer seats,
			@NotBlank(message = "Please enter the registration number")
			@Size(max = 20, message = "That's too long for a registration number") String plate) {
	}

	/** A car as its owner sees it, with the full registration number. */
	public record VehicleView(UUID id, String make, String model, String color, int seats, String plate,
			Instant createdAt) {
	}
}
