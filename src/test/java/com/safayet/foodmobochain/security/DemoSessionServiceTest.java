package com.safayet.foodmobochain.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoSessionServiceTest {
    private final String secret = Base64.getEncoder().encodeToString(new byte[32]);
    private final String staging = "https://food-mobo-chain-staging.onrender.com";

    private DemoSessionService service() {
        return new DemoSessionService(secret, staging, true, false, true);
    }

    @Test void oneClickBuyerAdminAndAllEightSellersGetShortLivedReadOnlyScopes() {
        DemoSessionService service = service();
        assertTrue(service.isEnabled());
        for (String role : List.of("buyer", "admin")) {
            var selected = service.choose(role, null);
            var verified = service.verify(service.issue(selected));
            assertEquals(selected, verified);
            assertEquals("", verified.storeSlug());
        }
        assertEquals(8, DemoSessionService.STORES.size());
        for (var store : DemoSessionService.STORES) {
            var selected = service.choose("seller", store.slug());
            assertEquals(selected, service.verify(service.issue(selected)));
        }
    }

    @Test void rejectsUnknownRoleOrSellerAndDisallowsNonStagingDeployment() {
        DemoSessionService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.choose("superadmin", null));
        assertThrows(IllegalArgumentException.class, () -> service.choose("seller", "not-listed"));
        assertThrows(IllegalArgumentException.class, () -> service.choose("seller", null));
        var prod = new DemoSessionService(secret, "https://foodmobo.example.com", true, false, true);
        assertFalse(prod.isEnabled());
        assertThrows(IllegalStateException.class, () -> prod.choose("admin", null));
        var defaultOff = new DemoSessionService(secret, staging, false, false, true);
        assertFalse(defaultOff.isEnabled());
    }

    @Test void demoTokenNeverAuthenticatesAsARealUserAndViceVersa() {
        var service = service();
        var realAccounts = new JwtService(secret);
        String demo = service.issue(service.choose("admin", null));
        String user = realAccounts.generateToken("admin@foodmobo.local", 0L);
        assertThrows(JwtException.class, () -> realAccounts.verifiedAccount(demo));
        assertThrows(JwtException.class, () -> service.verify(user));
    }

    @Test void signatureTamperingIsRejected() {
        var service = service();
        String jwt = service.issue(service.choose("buyer", null));
        String broken = jwt.substring(0, jwt.lastIndexOf('.') + 1) + "invalidSignature";
        assertThrows(JwtException.class, () -> service.verify(broken));
        assertThrows(IllegalArgumentException.class, () -> service.verify(""));
    }

    @Test void demoCookieIsHttpOnlySecurePathRestrictedAndCanBeCleared() {
        var service = service();
        HttpServletResponse response = mock(HttpServletResponse.class);
        service.issueCookie(response, service.choose("buyer", null));
        var header = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        String value = header.getValue();
        assertTrue(value.contains("FMC_DEMO="));
        assertTrue(value.contains("Path=/demo"));
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("SameSite=Lax"));
        assertFalse(value.contains("FMC_ACCESS"));
        clearInvocations(response);
        service.clearCookie(response);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        assertTrue(header.getValue().contains("Max-Age=0"));
    }

    @Test void ciLoopbackCanBeEnabledButOtherHostsCannot() {
        var allowed = new DemoSessionService(secret, "http://127.0.0.1:10000", true, true, false);
        assertTrue(allowed.isEnabled());
        assertFalse(new DemoSessionService(secret, "http://localhost:10000", true, true, false).isEnabled());
        assertFalse(new DemoSessionService(secret, "http://127.0.0.1:10000", true, false, false).isEnabled());
    }
}
