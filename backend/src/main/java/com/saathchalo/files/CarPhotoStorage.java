package com.saathchalo.files;

import com.saathchalo.config.AppProperties;
import org.springframework.stereotype.Component;

/** Public photos of rental cars ({@code cars/<name>.jpg}), re-encoded at most {@link #MAX_SIDE} pixels on the longer side. */
@Component
public class CarPhotoStorage extends PublicImages {

	static final int MAX_SIDE = 1600;

	public CarPhotoStorage(AppProperties props) {
		super(props, "cars", MAX_SIDE, "a photo of the car");
	}
}
