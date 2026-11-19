package com.saathchalo.config;

import com.saathchalo.common.FieldEncryptor;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	/** SaathChalo serves India, so calendar logic (age, ride dates) uses IST. */
	public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	FieldEncryptor fieldEncryptor(AppProperties props) {
		return new FieldEncryptor(Base64.getDecoder().decode(props.security().dataKey()));
	}
}
