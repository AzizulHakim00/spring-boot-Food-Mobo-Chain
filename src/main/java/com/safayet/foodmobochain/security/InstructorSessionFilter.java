package com.safayet.foodmobochain.security;

import com.safayet.foodmobochain.service.InstructorAccessService;
import com.safayet.foodmobochain.service.InstructorAccessService.InstructorSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Server-side instructor session authenticates ordinary Spring MVC dashboard
 * requests as a pre-existing demo identity. Does not mint a bearer/JWT token.
 * Expiry and revocation are checked against MongoDB on EVERY request.
 */
@RequiredArgsConstructor
public class InstructorSessionFilter extends OncePerRequestFilter {
    private final InstructorAccessService access;
    private final UserDetailsService userDetails;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/") || path.startsWith("/payment/ssl/")
                || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/demo-assets/")
                || path.startsWith("/favicon") || path.equals("/health") || path.equals("/ready")
                || path.equals("/login") || path.equals("/instructor/accept");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = InstructorAccessService.cookieValue(request);
        InstructorSession session = access.session(raw);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }

        UserDetails current;
        try {
            current = userDetails.loadUserByUsername(InstructorAccessService.accountEmail(session));
        } catch (org.springframework.security.core.AuthenticationException invalid) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!current.isEnabled() || !current.isAccountNonLocked()
                || !current.isAccountNonExpired() || !current.isCredentialsNonExpired()
                || current.getAuthorities().stream().noneMatch(a ->
                        a.getAuthority().equals("ROLE_" + session.role().toUpperCase(java.util.Locale.ROOT)))) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        // Instructor cannot reissue privileged invitations or break the ten
        // pre-provisioned showcase identities by changing their credentials.
        if (path.startsWith("/admin/instructor-access")
                || ("POST".equals(method) && "/account/password".equals(path))
                || ("POST".equals(method)
                    && path.matches("/admin/users/users:(?:[1-9]|10)/toggle"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        current, null, current.getAuthorities()));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        ((UsernamePasswordAuthenticationToken) authentication)
                .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        request.setAttribute(InstructorAccessService.REQUEST_ATTRIBUTE, session);

        boolean mutating = "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
        if (mutating) {
            // Record intent before a write is performed; refuse the request if
            // audit storage is unavailable rather than permit an unaudited write.
            access.audit("WRITE_START_" + method, session.role(), path, 0);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (mutating) {
                try {
                    access.audit("WRITE_END_" + method, session.role(), path, response.getStatus());
                } catch (RuntimeException failed) {
                    // Request intent has already been durably recorded.
                    logger.warn("Instructor audit outcome write failed without including request details.");
                }
            }
        }
    }
}
