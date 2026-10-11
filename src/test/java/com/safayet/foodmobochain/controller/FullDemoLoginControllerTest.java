package com.safayet.foodmobochain.controller;

import com.mongodb.client.MongoDatabase;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.security.JwtService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FullDemoLoginControllerTest {
    private final UserService users = mock(UserService.class);
    private final JwtService tokens = mock(JwtService.class);
    private final JwtCookieService cookies = mock(JwtCookieService.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final FullDemoLoginController controller = new FullDemoLoginController(users, tokens, cookies, mongo);

    private void isolated(String db, String url, boolean enabled) {
        MongoDatabase database = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(database);
        when(database.getName()).thenReturn(db);
        ReflectionTestUtils.setField(controller, "baseUrl", url);
        ReflectionTestUtils.setField(controller, "enabled", enabled);
    }

    @Test void adminBuyerAndEightSellersEnterRealRoleDashboardsWithStandardJwt() {
        isolated(FullDemoLoginController.DEMO_DATABASE, FullDemoLoginController.DEMO_URL, true);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(tokens.expiresInSeconds()).thenReturn(1800L);
        List<String> slugs = List.of("dhaka-biryani-house", "street-bite", "burger-station",
                "pizza-corner", "deshi-kitchen", "spice-route", "sweet-and-sip", "campus-cravings");
        for (int index = 0; index < 10; index++) {
            String role = index == 0 ? "admin" : index == 1 ? "buyer" : "seller";
            String sellerSlug = index < 2 ? null : slugs.get(index - 2);
            String email = index == 0 ? "admin@foodmobo.local" : index == 1 ?
                    "buyer@foodmobo.local" : "seller" + (index - 1) + "@foodmobo.local";
            Role userRole = index == 0 ? Role.ADMIN : index == 1 ? Role.BUYER : Role.SELLER;
            User account = User.builder().email(email).emailNormalized(email)
                    .role(userRole).enabled(true).emailVerified(true).sessionVersion(4L).build();
            when(users.getByEmail(email)).thenReturn(account);
            when(tokens.generateToken(email, 4L)).thenReturn("signed.test." + index);

            String actual = controller.enter(role, sellerSlug, response);

            assertEquals(index == 0 ? "redirect:/admin" : index == 1 ? "redirect:/" : "redirect:/seller", actual);
            verify(cookies).issue(response, "signed.test." + index, 1800L);
        }
        verify(response, times(10)).setHeader(eq("Cache-Control"), contains("no-store"));
    }

    @Test void refusesFullAccessOnExistingStagingOrAnyOtherDatabase() {
        isolated("food_mobo_chain", FullDemoLoginController.DEMO_URL, true);
        ResponseStatusException wrongDatabase = assertThrows(ResponseStatusException.class,
                () -> controller.enter("admin", null, mock(HttpServletResponse.class)));
        assertEquals(HttpStatus.NOT_FOUND, wrongDatabase.getStatusCode());
        isolated("food_mobo_chain_demo", "https://food-mobo-chain-staging.onrender.com", true);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.enter("admin", null, mock(HttpServletResponse.class))).getStatusCode());
        isolated("food_mobo_chain_demo", FullDemoLoginController.DEMO_URL, false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.enter("admin", null, mock(HttpServletResponse.class))).getStatusCode());
        verifyNoInteractions(users, cookies, tokens);
    }

    @Test void rejectsUnlistedAccountsAndDisabledOrWrongRoleUsers() {
        isolated("food_mobo_chain_demo", FullDemoLoginController.DEMO_URL, true);
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.enter("superuser", null, response)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.enter("seller", "unauthorized-store", response)).getStatusCode());
        when(users.getByEmail("admin@foodmobo.local")).thenReturn(User.builder()
                .email("admin@foodmobo.local").role(Role.ADMIN).enabled(false).emailVerified(true).build());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> controller.enter("admin", null, response)).getStatusCode());
        when(users.getByEmail("admin@foodmobo.local")).thenReturn(User.builder()
                .email("admin@foodmobo.local").role(Role.BUYER).enabled(true).emailVerified(true).build());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> controller.enter("admin", null, response)).getStatusCode());
        verifyNoInteractions(cookies, tokens);
    }
}
