package com.saathchalo.auth.staff;

import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.auth.StaffConfirmations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RequiresConfirmation} before the endpoint runs. */
@Component
class ConfirmationInterceptor implements HandlerInterceptor {

	private final StaffConfirmations confirmations;

	ConfirmationInterceptor(StaffConfirmations confirmations) {
		this.confirmations = confirmations;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RequiresConfirmation.class)) {
			confirmations.check(request);
		}
		return true;
	}
}
