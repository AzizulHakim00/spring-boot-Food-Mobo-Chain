package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.security.DemoSessionService;
import com.safayet.foodmobochain.service.CatalogService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAccessRedirectTest {
    private final DemoSessionService sessions = mock(DemoSessionService.class);
    private final DemoAccessController controller = new DemoAccessController(sessions, mock(CatalogService.class));
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test void redirectsEachValidatedRoleToTheFixedIsolatedDemoHost() {
        ReflectionTestUtils.setField(controller, "fullDemoUrl", "https://food-mobo-chain-demo.onrender.com");
        when(sessions.isEnabled()).thenReturn(true);
        for (String role : new String[]{"buyer", "admin"}) {
            when(sessions.choose(role, null)).thenReturn(new DemoSessionService.DemoScope(role, ""));
            assertEquals("redirect:https://food-mobo-chain-demo.onrender.com/demo/enter?role=" + role,
                    controller.start(role, null, response));
        }
        when(sessions.choose("seller", "spice-route"))
                .thenReturn(new DemoSessionService.DemoScope("seller", "spice-route"));
        assertEquals("redirect:https://food-mobo-chain-demo.onrender.com/demo/enter?role=seller&store=spice-route",
                controller.start("seller", "spice-route", response));
        verify(sessions, never()).issueCookie(any(), any());
    }

    @Test void unapprovedExternalUrlCannotRedirectLoginCredentials() {
        ReflectionTestUtils.setField(controller, "fullDemoUrl", "https://attacker.example");
        when(sessions.isEnabled()).thenReturn(true);
        when(sessions.choose("admin", null)).thenReturn(new DemoSessionService.DemoScope("admin", ""));
        assertEquals("redirect:/demo", controller.start("admin", null, response));
        verify(sessions).issueCookie(response, new DemoSessionService.DemoScope("admin", ""));
    }
}
