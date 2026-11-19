package com.saathchalo.admin.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class AdminWebConfig implements WebMvcConfigurer {

	private final AdminRateLimitInterceptor rateLimit;

	AdminWebConfig(AdminRateLimitInterceptor rateLimit) {
		this.rateLimit = rateLimit;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimit).addPathPatterns("/api/admin/**");
	}
}
