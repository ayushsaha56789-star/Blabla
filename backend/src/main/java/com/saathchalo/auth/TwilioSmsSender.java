package com.saathchalo.auth;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Text messages through Twilio ({@code SMS_PROVIDER=twilio}). Messages to Indian numbers need a DLT-registered sender
 * and templates (TRAI rules), set up in the Twilio console. The message text never reaches the log.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
class TwilioSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);

	private final RestClient api;

	private final String accountSid;

	private final String from;

	private final String messagingServiceSid;

	TwilioSmsSender(@Value("${app.sms.twilio.account-sid:}") String accountSid, @Value("${app.sms.twilio.auth-token:}") String authToken,
			@Value("${app.sms.twilio.from:}") String from, @Value("${app.sms.twilio.messaging-service-sid:}") String messagingServiceSid,
			@Value("${app.sms.twilio.base-url:https://api.twilio.com}") String baseUrl) {
		if (accountSid.isBlank() || authToken.isBlank() || (from.isBlank() && messagingServiceSid.isBlank())) {
			throw new IllegalStateException(
					"SMS_PROVIDER=twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM (or TWILIO_MESSAGING_SERVICE_SID)");
		}
		this.accountSid = accountSid;
		this.from = from;
		this.messagingServiceSid = messagingServiceSid;
		JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		http.setReadTimeout(Duration.ofSeconds(15));
		this.api = RestClient.builder()
			.requestFactory(http)
			.baseUrl(baseUrl)
			.defaultHeaders(h -> h.setBasicAuth(accountSid, authToken))
			.build();
	}

	@Override
	public void send(String phoneE164, String message) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", phoneE164);
		form.add("Body", message);
		if (messagingServiceSid.isBlank()) {
			form.add("From", from);
		}
		else {
			form.add("MessagingServiceSid", messagingServiceSid);
		}
		try {
			api.post()
				.uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException e) {
			log.warn("Text message to {} failed: {}", Masking.phone(phoneE164), e.getClass().getSimpleName());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "sms_failed",
					"We couldn't send the text message just now. Please try again in a minute.");
		}
	}
}
