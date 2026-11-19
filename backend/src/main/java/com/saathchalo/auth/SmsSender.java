package com.saathchalo.auth;

/** Sends text messages. Swap the implementation (MSG91, Twilio, …) with {@code app.sms.provider}. */
public interface SmsSender {

	void send(String phoneE164, String message);
}
