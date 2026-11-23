package com.saathchalo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saathchalo.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Twilio sender against a small local server standing in for Twilio: no real messages are sent. */
class TwilioSmsSenderTest {

	private HttpServer server;

	private final AtomicReference<String> path = new AtomicReference<>();

	private final AtomicReference<String> auth = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	private final AtomicInteger status = new AtomicInteger(201);

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			path.set(exchange.getRequestURI().getPath());
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
			byte[] reply = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), reply.length);
			exchange.getResponseBody().write(reply);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void sendsTheMessageWithTheAccountsKeys() {
		TwilioSmsSender sender = new TwilioSmsSender("AC123", "secret-token", "+15005550006", "", url());
		sender.send("+919812345678", "123456 is your SaathChalo login code.");
		assertThat(path.get()).isEqualTo("/2010-04-01/Accounts/AC123/Messages.json");
		assertThat(auth.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("AC123:secret-token".getBytes(StandardCharsets.UTF_8)));
		assertThat(body.get()).contains("To=+919812345678", "From=+15005550006", "Body=123456 is your SaathChalo login code.");
	}

	@Test
	void aMessagingServiceCanSendInsteadOfANumber() {
		new TwilioSmsSender("AC123", "secret-token", "", "MG999", url()).send("+919812345678", "Hi");
		assertThat(body.get()).contains("MessagingServiceSid=MG999").doesNotContain("From=");
	}

	@Test
	void aFailureBecomesAFriendlyMessage() {
		status.set(400);
		TwilioSmsSender sender = new TwilioSmsSender("AC123", "secret-token", "+15005550006", "", url());
		assertThatThrownBy(() -> sender.send("+919812345678", "Hi")).isInstanceOf(ApiException.class)
			.hasMessageContaining("couldn't send the text message");
	}

	@Test
	void missingKeysStopTheServerStarting() {
		assertThatThrownBy(() -> new TwilioSmsSender("", "x", "+1", "", url())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_ACCOUNT_SID");
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}
}
