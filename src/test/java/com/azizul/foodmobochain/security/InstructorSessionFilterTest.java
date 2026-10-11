package com.azizul.foodmobochain.security;

import com.azizul.foodmobochain.model.User;
import com.azizul.foodmobochain.model.enums.Role;
import com.azizul.foodmobochain.service.InstructorAccessService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstructorSessionFilterTest {
    private final InstructorAccessService access = mock(InstructorAccessService.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final InstructorSessionFilter filter = new InstructorSessionFilter(access, users);

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String uri) {
        var r = new MockHttpServletRequest(method, uri);
        r.setCookies(new Cookie(InstructorAccessService.COOKIE, AccountTokens.create()));
        return r;
    }

    private InstructorAccessService.InstructorSession enabledSession() {
        return new InstructorAccessService.InstructorSession("professor@university.example",
                "admin", 0, Instant.now().plusSeconds(600));
    }

    @Test void instructorBecomesARealAuthenticatedAdminButDoesNotReceiveJwt() throws Exception {
        var request = request("GET", "/admin");
        when(access.session(anyString())).thenReturn(enabledSession());
        when(users.loadUserByUsername("admin@foodmobo.local")).thenReturn(
                new AccountUserDetails(User.builder().email("admin@foodmobo.local")
                        .role(Role.ADMIN).enabled(true).emailVerified(true)
                        .password("unused-bcrypt-hash").build()));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").contains("ROLE_ADMIN");
        assertThat(request.getAttribute(InstructorAccessService.REQUEST_ATTRIBUTE))
                .isInstanceOf(InstructorAccessService.InstructorSession.class);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test void instructorCannotChangeSharedPasswordsOrReissueInvitations() throws Exception {
        for (String path : new String[]{"/account/password", "/admin/users/users:1/toggle",
                "/admin/instructor-access/invite"}) {
            SecurityContextHolder.clearContext();
            var request = request("POST", path);
            when(access.session(anyString())).thenReturn(enabledSession());
            when(users.loadUserByUsername("admin@foodmobo.local")).thenReturn(
                    new AccountUserDetails(User.builder().email("admin@foodmobo.local")
                            .role(Role.ADMIN).enabled(true).emailVerified(true).password("x").build()));
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(403);
            verify(access, never()).audit(eq("WRITE_START_POST"), anyString(), anyString(), anyInt());
        }
    }

    @Test void apiRequestsDoNotAcceptInstructorCookiesAsBearerAuthentication() throws Exception {
        var req = request("POST", "/api/admin/users");
        assertThat(filter.shouldNotFilter(req)).isTrue();
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(access, users);
    }

    @Test void disabledOrExpiredSessionDoesNotCreateAnIdentity() throws Exception {
        var req = request("GET", "/seller");
        when(access.session(anyString())).thenReturn(null);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(users);
    }
}
