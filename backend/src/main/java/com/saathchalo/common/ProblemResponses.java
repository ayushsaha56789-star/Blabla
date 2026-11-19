package com.saathchalo.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

/** Writes the same problem-details JSON as {@link GlobalExceptionHandler}, for use in servlet filters. */
public final class ProblemResponses {

	private ProblemResponses() {
	}

	public static void write(HttpServletResponse response, ObjectMapper json, HttpStatus status, String code,
			String detail, Map<String, Object> extra) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("code", code);
		if (extra != null) {
			extra.forEach((k, v) -> {
				if (v != null) {
					body.put(k, v);
				}
			});
		}
		response.setStatus(status.value());
		response.setContentType("application/problem+json");
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), body);
	}
}
