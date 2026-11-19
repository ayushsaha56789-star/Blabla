package com.saathchalo.auth;

import com.saathchalo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development stand-in for a real SMS gateway: writes the message to the server log. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
class ConsoleSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

	ConsoleSmsSender(AppProperties props) {
		if (!props.otp().exposeCode()) {
			log.warn("SMS provider is 'console': login codes are only written to this log, not texted. "
					+ "Configure a real SMS provider (app.sms.provider) before going live.");
		}
	}

	@Override
	public void send(String phoneE164, String message) {
		log.info("[SMS to {}] {}", phoneE164, message);
	}
}
