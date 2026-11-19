package com.saathchalo.auth.staff;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class AuthWebConfig implements WebMvcConfigurer {

	private final ConfirmationInterceptor confirmation;

	AuthWebConfig(ConfirmationInterceptor confirmation) {
		this.confirmation = confirmation;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(confirmation).addPathPatterns("/api/admin/**");
	}
}
