package com.saathchalo.verification;

/** Sends plain-text emails. Development uses {@link ConsoleEmailSender}; a real provider comes before launch. */
public interface EmailSender {

	void send(String to, String subject, String body);
}
