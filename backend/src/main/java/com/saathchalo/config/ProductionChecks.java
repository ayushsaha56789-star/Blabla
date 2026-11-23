package com.saathchalo.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a live server with settings that are only safe on a developer's computer: login codes shown on
 * screen, pretend payments, text messages and emails written to the log, weak secrets or insecure cookies. Each
 * problem is listed with the setting to change. The dev and test profiles are exempt.
 */
@Component
class ProductionChecks implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(ProductionChecks.class);

	private final Environment env;

	ProductionChecks(Environment env) {
		this.env = env;
	}

	@Override
	public void afterPropertiesSet() {
		if (exempt(env)) {
			return;
		}
		List<String> problems = problems(env);
		if (!problems.isEmpty()) {
			throw new IllegalStateException("SaathChalo can't start with these production settings:\n  - "
					+ String.join("\n  - ", problems));
		}
		if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
			log.warn("The API documentation (Swagger) is switched on. Switch it off on a public server.");
		}
	}

	static boolean exempt(Environment env) {
		Set<String> profiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));
		return profiles.contains("dev") || profiles.contains("test");
	}

	static List<String> problems(Environment env) {
		List<String> problems = new ArrayList<>();
		if (env.getProperty("app.otp.expose-code", Boolean.class, false)) {
			problems.add("app.otp.expose-code must be false: login codes would be shown to anyone.");
		}
		String jwt = env.getProperty("app.jwt.secret", "");
		if (jwt.length() < 32) {
			problems.add("JWT_SECRET must be a random value of at least 32 characters.");
		}
		if (env.getProperty("app.security.data-key", "").isBlank()) {
			problems.add("DATA_ENCRYPTION_KEY must be set (base64 of 32 random bytes).");
		}
		if (!env.getProperty("app.auth.refresh-cookie-secure", Boolean.class, true)) {
			problems.add("app.auth.refresh-cookie-secure must be true: login cookies must only travel over HTTPS.");
		}
		if ("fake".equalsIgnoreCase(env.getProperty("app.payments.provider", "none"))) {
			problems.add("PAYMENTS_PROVIDER=fake pretends payments succeeded. Use razorpay, or none to switch online payments off.");
		}
		if (!env.getProperty("app.public-url", "").startsWith("https://")) {
			problems.add("PUBLIC_URL must be the site's https:// address.");
		}
		if (!env.getProperty("app.launch.allow-console-senders", Boolean.class, false)) {
			if ("console".equalsIgnoreCase(env.getProperty("app.sms.provider", "console"))) {
				problems.add("SMS_PROVIDER is console: login codes would only be written to the log. Set up a real SMS provider.");
			}
			if ("console".equalsIgnoreCase(env.getProperty("app.email.provider", "console"))) {
				problems.add("EMAIL_PROVIDER is console: email codes would only be written to the log. Set up a real email provider.");
			}
		}
		return problems;
	}
}
