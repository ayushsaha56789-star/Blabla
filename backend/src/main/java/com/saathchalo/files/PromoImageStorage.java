package com.saathchalo.files;

import com.saathchalo.config.AppProperties;
import org.springframework.stereotype.Component;

/** Public pictures for promotions ({@code promos/<name>.jpg}), re-encoded at most 1,600 pixels on the longer side. */
@Component
public class PromoImageStorage extends PublicImages {

	public PromoImageStorage(AppProperties props) {
		super(props, "promos", 1600, "a picture");
	}
}
