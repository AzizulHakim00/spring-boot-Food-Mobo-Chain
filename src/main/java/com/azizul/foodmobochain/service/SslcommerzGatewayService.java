package com.azizul.foodmobochain.service;

import com.azizul.foodmobochain.model.CustomerOrder;
import com.azizul.foodmobochain.model.Payment;
import com.azizul.foodmobochain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hosted SSLCOMMERZ checkout, SANDBOX ONLY. Real money must never be collected
 * using this class. All API calls and credential use stay on the server.
 */
@Service
public class SslcommerzGatewayService {
    private static final String INIT_URL = "https://sandbox-gw.sslcommerz.com/gwprocess/v4/api.php";
    private static final String VALIDATION_URL =
            "https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php";
    private static final Set<String> REDIRECT_HOSTS =
            Set.of("sandbox.sslcommerz.com", "sandbox-gw.sslcommerz.com");

    private final RestClient client;
    private final String storeId;
    private final String storePassword;
    private final String callbackBase;
    private final boolean enabled;

    public SslcommerzGatewayService(
            @Value("${app.sslcommerz.store-id:}") String storeId,
            @Value("${app.sslcommerz.store-password:}") String storePassword,
            @Value("${app.sslcommerz.mode:demo}") String mode,
            @Value("${app.public-base-url:https://food-mobo-chain-staging.onrender.com}") String callbackBase) {
        this.storeId = storeId == null ? "" : storeId.trim();
        this.storePassword = storePassword == null ? "" : storePassword.trim();
        this.enabled = "sandbox".equalsIgnoreCase(mode)
                && !this.storeId.isBlank() && !this.storePassword.isBlank();
        this.callbackBase = callbackBase.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public GatewaySession initiate(CustomerOrder order, User buyer) {
        requireEnabled();
        Payment payment = order.getPayment();
        if (payment == null || payment.getAmount() == null
                || payment.getAmount().compareTo(new BigDecimal("10.00")) < 0
                || payment.getAmount().compareTo(new BigDecimal("500000.00")) > 0) {
            throw new IllegalArgumentException("Sandbox payments must be between BDT 10 and BDT 500,000.");
        }
        if (payment.getTransactionId() == null || payment.getTransactionId().length() > 30) {
            throw new IllegalArgumentException("Gateway transaction ID is invalid.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id", storeId);
        form.add("store_passwd", storePassword);
        form.add("total_amount", payment.getAmount().toPlainString());
        form.add("currency", "BDT");
        form.add("tran_id", payment.getTransactionId());
        form.add("success_url", callbackBase + "/payment/ssl/success");
        form.add("fail_url", callbackBase + "/payment/ssl/fail");
        form.add("cancel_url", callbackBase + "/payment/ssl/cancel");
        form.add("ipn_url", callbackBase + "/payment/ssl/ipn");
        form.add("cus_name", limit(buyer.getFullName(), 50, "Food Mobo Chain Buyer"));
        form.add("cus_email", limit(buyer.getEmail(), 50, "buyer@example.com"));
        form.add("cus_add1", limit(order.getDeliveryAddress(), 50, "Dhaka"));
        form.add("cus_city", "Dhaka");
        form.add("cus_postcode", "1200");
        form.add("cus_country", "Bangladesh");
        form.add("cus_phone", limit(order.getPhone(), 20, "01711111111"));
        form.add("shipping_method", "NO");
        form.add("product_name", "Food Mobo Chain order " + order.getOrderNumber());
        form.add("product_category", "Food");
        form.add("product_profile", "general");
        form.add("value_a", order.getOrderNumber());

        final Map<?, ?> result;
        try {
            result = client.post().uri(INIT_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
        } catch (RestClientException exception) {
            // Avoid leaking credentials (including those in exception URLs) into public messages.
            throw new IllegalStateException("SSLCOMMERZ Sandbox is temporarily unavailable.");
        }
        if (result == null || !"SUCCESS".equalsIgnoreCase(String.valueOf(result.get("status")))) {
            throw new IllegalStateException("SSLCOMMERZ did not create a sandbox payment session.");
        }
        String gatewayUrl = String.valueOf(result.get("GatewayPageURL"));
        String sessionKey = String.valueOf(result.get("sessionkey"));
        try {
            URI uri = URI.create(gatewayUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getHost() == null
                    || !REDIRECT_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SSLCOMMERZ returned an invalid checkout address.");
        }
        return new GatewaySession(gatewayUrl, sessionKey);
    }

    public VerifiedPayment verify(String validationId) {
        requireEnabled();
        if (validationId == null || !validationId.matches("[A-Za-z0-9_-]{5,80}")) {
            throw new IllegalArgumentException("Invalid payment validation identifier.");
        }
        URI uri = UriComponentsBuilder.fromUriString(VALIDATION_URL)
                .queryParam("val_id", validationId)
                .queryParam("store_id", storeId)
                .queryParam("store_passwd", storePassword)
                .queryParam("format", "json")
                .build().encode().toUri();
        final Map<?, ?> result;
        try {
            result = client.get().uri(uri).retrieve().body(Map.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Unable to verify payment with SSLCOMMERZ Sandbox.");
        }
        if (result == null || !Set.of("VALID", "VALIDATED")
                .contains(String.valueOf(result.get("status")).toUpperCase(Locale.ROOT))) {
            throw new SecurityException("The gateway has not confirmed this payment.");
        }
        if (!"0".equals(String.valueOf(result.get("risk_level")))) {
            throw new SecurityException("Payment requires additional manual verification.");
        }
        try {
            String currency = result.get("currency_type") != null
                    ? String.valueOf(result.get("currency_type")) : String.valueOf(result.get("currency"));
            BigDecimal amount = new BigDecimal(String.valueOf(
                    result.get("currency_amount") != null
                            ? result.get("currency_amount") : result.get("amount")));
            return new VerifiedPayment(
                    String.valueOf(result.get("tran_id")),
                    amount,
                    currency,
                    String.valueOf(result.get("bank_tran_id")));
        } catch (NumberFormatException exception) {
            throw new SecurityException("The gateway returned an invalid payment amount.");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Sandbox gateway is not enabled.");
        }
    }

    private static String limit(String value, int max, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value.trim();
        return candidate.length() <= max ? candidate : candidate.substring(0, max);
    }

    public record GatewaySession(String gatewayUrl, String sessionKey) { }

    public record VerifiedPayment(String transactionId, BigDecimal amount,
                                  String currency, String bankTransactionId) { }
}
