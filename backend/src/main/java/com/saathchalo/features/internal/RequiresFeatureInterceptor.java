package com.saathchalo.features.internal;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.FeatureService;
import com.saathchalo.features.RequiresFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RequiresFeature} on the server, whatever the website shows. */
@Component
class RequiresFeatureInterceptor implements HandlerInterceptor {

	private final FeatureService features;

	RequiresFeatureInterceptor(FeatureService features) {
		this.features = features;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		RequiresFeature required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresFeature.class);
		if (required == null) {
			required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
		}
		if (required != null) {
			var userId = CurrentUser.id().orElse(null);
			for (String key : required.value()) {
				if (!features.isOnFor(key, userId)) {
					throw ApiException.forbidden("feature_unavailable", "This feature isn't available right now.")
						.with("feature", key);
				}
			}
		}
		return true;
	}
}
