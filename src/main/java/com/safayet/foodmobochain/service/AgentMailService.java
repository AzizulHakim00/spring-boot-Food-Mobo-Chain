package com.safayet.foodmobochain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends account links through AgentMail HTTPS. Secrets and links are never logged. */
@Service
public class AgentMailService {
    private final String apiKey;
    private final String inboxId;
    private final String publicBaseUrl;
    private final URI endpoint;
    private final RestClient client;

    @Autowired
    public AgentMailService(
            @Value("${app.mail.agentmail-api-key:}") String apiKey,
            @Value("${app.mail.inbox-id:}") String inboxId,
            @Value("${app.public-base-url:https://food-mobo-chain-staging.onrender.com}") String publicBaseUrl,
            @Value("${app.mail.api-url:https://api.agentmail.to}") String apiUrl,
            @Value("${app.mail.allow-loopback-http:false}") boolean allowLoopbackHttp) {
        this(apiKey, inboxId, publicBaseUrl, apiUrl, allowLoopbackHttp, makeClient());
    }

    AgentMailService(String apiKey, String inboxId, String publicBaseUrl, String apiUrl,
                     boolean allowLoopbackHttp, RestClient client) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.inboxId = inboxId == null ? "" : inboxId.trim();
        this.publicBaseUrl = checkedAddress(publicBaseUrl, allowLoopbackHttp, false);
        String base = checkedAddress(apiUrl, allowLoopbackHttp, true);
        this.endpoint = URI.create(base + "/v0/inboxes/"
                + URLEncoder.encode(this.inboxId, StandardCharsets.UTF_8) + "/messages/send");
        this.client = client;
    }

    private static RestClient makeClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String checkedAddress(String address, boolean allowLoopbackHttp, boolean api) {
        URI uri;
        try { uri = URI.create(address); }
        catch (RuntimeException invalid) { throw new IllegalStateException("Invalid email URL configuration"); }
        boolean loopback = allowLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme())
                && "127.0.0.1".equals(uri.getHost());
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if ((!https && !loopback) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalStateException("Invalid email URL configuration");
        if (api && !loopback && !address.equals("https://api.agentmail.to"))
            throw new IllegalStateException("Mail API must use the official AgentMail endpoint");
        return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.contains("\r") && !apiKey.contains("\n")
                && inboxId.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+") && inboxId.length() <= 254;
    }

    public void requireConfigured() {
        if (!isConfigured()) throw new MailUnavailableException(
                "Email delivery is temporarily unavailable. Please try again later.");
    }

    public void sendVerification(String email, String rawToken) {
        send(email, "Verify your Food Mobo Chain email",
                "Welcome to Food Mobo Chain!\n\nOpen this link to verify your email address:\n"
                        + publicBaseUrl + "/verify-email?token=" + rawToken
                        + "\n\nThis link expires in 24 hours. If you did not register, ignore this email.");
    }

    public void sendPasswordReset(String email, String rawToken) {
        send(email, "Reset your Food Mobo Chain password",
                "Someone requested a password reset for your Food Mobo Chain account.\n\n"
                        + "If this was you, open this link:\n"
                        + publicBaseUrl + "/reset-password?token=" + rawToken
                        + "\n\nThis link expires in 30 minutes and works only once."
                        + "\nIf you did not request it, ignore this email.");
    }

    private void send(String email, String subject, String text) {
        requireConfigured();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.post().uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", List.of(email), "subject", subject, "text", text))
                    .retrieve().body(Map.class);
            if (result == null || !(result.get("message_id") instanceof String id) || id.isBlank())
                throw new MailUnavailableException("Email provider did not confirm accepting the message.");
        } catch (RestClientException failed) {
            throw new MailUnavailableException("Email delivery failed. Please try again later.");
        }
    }

    public static class MailUnavailableException extends RuntimeException {
        public MailUnavailableException(String message) { super(message); }
    }
}
