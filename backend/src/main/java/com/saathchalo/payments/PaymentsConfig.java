package com.saathchalo.payments;

import com.saathchalo.config.AppProperties;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Picks the payment gateway from {@code app.payments.provider}, refusing unsafe set-ups at start-up. */
@Configuration
class PaymentsConfig {

	/** The gateway in use; empty when online payments aren't set up (members can still pay drivers directly). */
	record Gateways(PaymentGateway gateway) {

		Optional<PaymentGateway> active() {
			return Optional.ofNullable(gateway);
		}
	}

	@Bean
	Gateways paymentGateways(AppProperties props, Environment env) {
		AppProperties.Payments config = props.payments();
		return new Gateways(switch (config.provider().toLowerCase(Locale.ROOT)) {
			case "none" -> null;
			case "fake" -> {
				if (!env.acceptsProfiles(Profiles.of("dev", "test"))) {
					throw new IllegalStateException("The pretend payment gateway is for development only. "
							+ "Set PAYMENTS_PROVIDER=razorpay (with its keys) or none.");
				}
				yield new FakeGateway(Base64.getDecoder().decode(props.security().dataKey()));
			}
			case "razorpay" -> {
				if (blank(config.razorpayKeyId()) || blank(config.razorpayKeySecret()) || blank(config.razorpayWebhookSecret())) {
					throw new IllegalStateException(
							"Razorpay needs RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET.");
				}
				yield new RazorpayGateway(config);
			}
			default -> throw new IllegalStateException("app.payments.provider must be none, fake or razorpay");
		});
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
