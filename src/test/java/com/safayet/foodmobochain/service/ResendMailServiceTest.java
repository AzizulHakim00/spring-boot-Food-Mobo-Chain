package com.safayet.foodmobochain.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendMailServiceTest {
    @Test void sendsVerificationThroughAuthenticatedHttpsApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String raw = "safeBase64UrlToken01234567890123456789012";
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_only"))
                .andExpect(jsonPath("$.to[0]").value("buyer@example.test"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("/verify-email?token=" + raw)))
                .andRespond(withSuccess("{\"id\":\"accepted\"}", MediaType.APPLICATION_JSON));
        ResendMailService mail = new ResendMailService("re_test_only", "Food Mobo <no-reply@example.test>",
                "https://food-mobo-chain-staging.onrender.com", "https://api.resend.com/emails", false, builder.build());

        mail.sendVerification("buyer@example.test", raw);
        server.verify();
    }

    @Test void noCredentialsCannotPretendEmailWasSent() {
        ResendMailService mail = new ResendMailService("", "", "https://food-mobo-chain-staging.onrender.com",
                "https://api.resend.com/emails", false, RestClient.create());
        assertThat(mail.isConfigured()).isFalse();
        assertThatThrownBy(() -> mail.sendPasswordReset("buyer@example.test", AccountTokensForMailTestToken()))
                .isInstanceOf(ResendMailService.MailUnavailableException.class);
    }

    @Test void rejectsAttackerControlledApiHostAndNonTlsPublicLinks() {
        assertThatThrownBy(() -> new ResendMailService("key", "user@example.test",
                "https://food-mobo-chain-staging.onrender.com", "https://external.example/emails",
                false, RestClient.create())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ResendMailService("key", "user@example.test",
                "http://untrusted.example", "https://api.resend.com/emails",
                false, RestClient.create())).isInstanceOf(IllegalStateException.class);
    }

    private static String AccountTokensForMailTestToken() { return "test-token-that-does-not-need-network"; }
}
