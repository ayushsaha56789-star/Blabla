package com.saathchalo.verification;

import com.saathchalo.common.Masking;
import com.saathchalo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development stand-in for a real email service: writes the message to the server log. */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "console", matchIfMissing = true)
class ConsoleEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

	ConsoleEmailSender(AppProperties props) {
		if (!props.otp().exposeCode()) {
			log.warn("Email provider is 'console': emails are only written to this log, not sent. "
					+ "Configure a real email provider (app.email.provider) before going live.");
		}
	}

	@Override
	public void send(String to, String subject, String body) {
		log.info("[Email to {}] {}\n{}", Masking.email(to), subject, body);
	}
}
