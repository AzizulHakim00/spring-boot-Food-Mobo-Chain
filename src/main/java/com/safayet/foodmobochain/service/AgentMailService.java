package com.safayet.foodmobochain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends account links through AgentMail HTTPS. Secrets and links are never logged. */
@Service
public class AgentMailService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentMailService.class);
    private static final java.util.regex.Pattern ERROR_CODE = java.util.regex.Pattern.compile(
            "\\\"code\\\"\\s*:\\s*\\\"([a-z_]{3,60})\\\"");
    private static final java.util.Set<String> KNOWN_ERRORS = java.util.Set.of(
            "missing_authorization", "invalid_token_type", "unknown_api_key", "api_key_expired",
            "unauthorized", "missing_permission", "forbidden", "not_found", "validation_error",
            "unprocessable", "domain_not_verified", "message_rejected", "rate_limit_exceeded",
            "service_unavailable", "internal_error", "limit_exceeded");
    private final String apiKey;
    private final String inboxId;
    private final String publicBaseUrl;
    private final URI inboxEndpoint;
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
        this.inboxEndpoint = URI.create(base + "/v0/inboxes/"
                + URLEncoder.encode(this.inboxId, StandardCharsets.UTF_8));
        this.endpoint = URI.create(inboxEndpoint + "/messages/send");
        this.client = client;
        // Safe startup diagnostics only. Never log credentials, recipients, or token URLs.
        LOG.info("AgentMail configuration: keyPresent={}, keyHasExpectedPrefix={}, inboxConfigured={}",
                !this.apiKey.isBlank(), this.apiKey.startsWith("am_"),
                this.inboxId.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+"));
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

    /**
     * Diagnostic only: a harmless message to this application's own inbox.
     * Invoked solely when APP_MAIL_SELF_TEST_ON_START=true on staging.
     * No account, password-reset or verification tokens are involved.
     */
    public void sendSelfDiagnostic() {
        send(inboxId, "Food Mobo Chain sender check",
                "Harmless self-test from the staging application to its own AgentMail inbox.");
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

    /**
     * Read-only staging diagnostic using the same Bearer credential as send.
     * Reports only an HTTP status and response type, never an inbox or secret.
     */
    public void diagnosticInboxRead() {
        requireConfigured();
        try {
            client.get().uri(inboxEndpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve().toBodilessEntity();
            LOG.info("AgentMail Render-key inbox-read was accepted");
        } catch (RestClientResponseException denied) {
            LOG.warn("AgentMail Render-key inbox-read rejected: status={}, code={}, responseType={}",
                    denied.getStatusCode().value(),
                    safeErrorCode(denied.getResponseBodyAsString()), responseKind(denied));
        } catch (RestClientException transport) {
            LOG.warn("AgentMail Render-key inbox-read transport failed without HTTP response");
        }
    }

    private static String responseKind(RestClientResponseException response) {
        org.springframework.http.HttpHeaders headers = response.getResponseHeaders();
        if (headers == null || headers.getContentType() == null) return "unknown";
        org.springframework.http.MediaType type = headers.getContentType();
        if (type.isCompatibleWith(MediaType.APPLICATION_JSON)) return "json";
        if (type.isCompatibleWith(MediaType.TEXT_HTML)) return "html";
        return "other";
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
            if (result == null || !(result.get("message_id") instanceof String id) || id.isBlank()) {
                LOG.warn("AgentMail response did not include a confirmed message ID");
                throw new MailUnavailableException("Email delivery is unavailable. Please try again later.");
            }
        } catch (RestClientResponseException rejected) {
            // Never log the response body, HTTP headers, recipient, or reset/verification link.
            // AgentMail's public error codes are stable; only explicitly allowed codes are logged.
            LOG.warn("AgentMail HTTPS send rejected: status={}, code={}, responseType={}",
                    rejected.getStatusCode().value(), safeErrorCode(rejected.getResponseBodyAsString()),
                    responseKind(rejected));
            throw new MailUnavailableException("Email delivery is unavailable. Please try again later.");
        } catch (RestClientException failed) {
            LOG.warn("AgentMail HTTPS transport failure (no HTTP response)");
            throw new MailUnavailableException("Email delivery is unavailable. Please try again later.");
        }
    }

    private static String safeErrorCode(String json) {
        if (json == null) return "unknown";
        java.util.regex.Matcher match = ERROR_CODE.matcher(json);
        if (!match.find()) return "unknown";
        String code = match.group(1);
        return KNOWN_ERRORS.contains(code) ? code : "unknown";
    }

    public static class MailUnavailableException extends RuntimeException {
        public MailUnavailableException(String message) { super(message); }
    }
}
