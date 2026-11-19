package com.saathchalo.common;

import java.util.List;

/** One page of results. {@code page} starts at 0. */
public record PageResult<T>(List<T> items, long total, int page, int size) {

	public static int clampSize(int size) {
		return Math.max(1, Math.min(size, 100));
	}
}
