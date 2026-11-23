package com.saathchalo.verification;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email through any SMTP service ({@code EMAIL_PROVIDER=smtp}): Amazon SES, Zoho, Brevo, Google Workspace and others.
 * The connection must be encrypted (STARTTLS on port 587, or TLS on port 465). The email text never reaches the log.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSenderImpl mail;

	private final InternetAddress from;

	SmtpEmailSender(@Value("${app.email.smtp.host:}") String host, @Value("${app.email.smtp.port:587}") int port,
			@Value("${app.email.smtp.username:}") String username, @Value("${app.email.smtp.password:}") String password,
			@Value("${app.email.from:}") String fromAddress, @Value("${app.email.from-name:SaathChalo}") String fromName)
			throws UnsupportedEncodingException {
		if (host.isBlank() || fromAddress.isBlank()) {
			throw new IllegalStateException("EMAIL_PROVIDER=smtp needs SMTP_HOST and EMAIL_FROM (and usually SMTP_USERNAME and SMTP_PASSWORD)");
		}
		this.from = new InternetAddress(fromAddress, fromName, "UTF-8");
		this.mail = new JavaMailSenderImpl();
		mail.setHost(host);
		mail.setPort(port);
		mail.setDefaultEncoding("UTF-8");
		if (!username.isBlank()) {
			mail.setUsername(username);
			mail.setPassword(password);
		}
		Properties p = mail.getJavaMailProperties();
		p.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
		p.put("mail.smtp.connectiontimeout", "10000");
		p.put("mail.smtp.timeout", "15000");
		p.put("mail.smtp.writetimeout", "15000");
		if (port == 465) {
			p.put("mail.smtp.ssl.enable", "true");
		}
		else {
			p.put("mail.smtp.starttls.enable", "true");
			p.put("mail.smtp.starttls.required", "true");
		}
		p.put("mail.smtp.ssl.checkserveridentity", "true");
	}

	@Override
	public void send(String to, String subject, String body) {
		try {
			var message = mail.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body, false);
			mail.send(message);
		}
		catch (MailException | jakarta.mail.MessagingException e) {
			log.warn("Email to {} failed: {}", Masking.email(to), e.getClass().getSimpleName());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_failed",
					"We couldn't send the email just now. Please try again in a minute.");
		}
	}
}
