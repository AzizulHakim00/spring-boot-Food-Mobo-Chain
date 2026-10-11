package com.safayet.foodmobochain.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AgentMailServiceTest {
    @Test void sendsVerificationThroughAuthenticatedHttpsApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String raw = "safeBase64UrlToken01234567890123456789012";
        server.expect(requestTo("https://api.agentmail.to/v0/inboxes/food-mobo-chain%40agentmail.to/messages/send"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer agentmail_test_only"))
                .andExpect(jsonPath("$.to[0]").value("buyer@example.test"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("/verify-email?token=" + raw)))
                .andRespond(withSuccess("{\"message_id\":\"accepted\",\"thread_id\":\"thread\"}", MediaType.APPLICATION_JSON));
        AgentMailService mail = new AgentMailService("agentmail_test_only", "food-mobo-chain@agentmail.to",
                "https://food-mobo-chain-staging.onrender.com", "https://api.agentmail.to", false, builder.build());

        mail.sendVerification("buyer@example.test", raw);
        server.verify();
    }

    @Test void selfDiagnosticOnlySendsToOwnConfiguredInboxWithoutAccountLinks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.agentmail.to/v0/inboxes/food-mobo-chain%40agentmail.to/messages/send"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer agentmail_test_only"))
                .andExpect(jsonPath("$.to[0]").value("food-mobo-chain@agentmail.to"))
                .andExpect(jsonPath("$.subject").value("Food Mobo Chain sender check"))
                .andRespond(withSuccess("{\"message_id\":\"accepted\",\"thread_id\":\"thread\"}", MediaType.APPLICATION_JSON));
        AgentMailService mail = new AgentMailService("agentmail_test_only", "food-mobo-chain@agentmail.to",
                "https://food-mobo-chain-staging.onrender.com", "https://api.agentmail.to", false, builder.build());
        mail.sendSelfDiagnostic();
        server.verify();
    }

    @Test void unauthorizedSenderReturnsGenericErrorWithoutSecrets() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.agentmail.to/v0/inboxes/food-mobo-chain%40agentmail.to/messages/send"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"missing_permission\",\"message\":\"secret error details\"}"));
        AgentMailService mail = new AgentMailService("agentmail_test_only", "food-mobo-chain@agentmail.to",
                "https://food-mobo-chain-staging.onrender.com", "https://api.agentmail.to", false, builder.build());
        assertThatThrownBy(() -> mail.sendSelfDiagnostic())
                .isInstanceOf(AgentMailService.MailUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("agentmail_test_only");
        server.verify();
    }

    @Test void noCredentialsCannotPretendEmailWasSent() {
        AgentMailService mail = new AgentMailService("", "", "https://food-mobo-chain-staging.onrender.com",
                "https://api.agentmail.to", false, org.mockito.Mockito.mock(RestClient.class));
        assertThat(mail.isConfigured()).isFalse();
        assertThatThrownBy(() -> mail.sendPasswordReset("buyer@example.test", AccountTokensForMailTestToken()))
                .isInstanceOf(AgentMailService.MailUnavailableException.class);
    }

    @Test void rejectsAttackerControlledApiHostAndNonTlsPublicLinks() {
        assertThatThrownBy(() -> new AgentMailService("key", "food-mobo-chain@agentmail.to",
                "https://food-mobo-chain-staging.onrender.com", "https://external.example",
                false, org.mockito.Mockito.mock(RestClient.class))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AgentMailService("key", "food-mobo-chain@agentmail.to",
                "http://untrusted.example", "https://api.agentmail.to",
                false, org.mockito.Mockito.mock(RestClient.class))).isInstanceOf(IllegalStateException.class);
    }

    private static String AccountTokensForMailTestToken() { return "test-token-that-does-not-need-network"; }
}
