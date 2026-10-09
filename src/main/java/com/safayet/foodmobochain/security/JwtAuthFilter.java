package com.safayet.foodmobochain.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Cookies authenticate MVC requests; /api/** only accepts a bearer token, never cookies. */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService tokens;
    private final UserDetailsService users;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/favicon") || (path.equals("/health") || path.equals("/ready"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String token = path.startsWith("/api/") ? bearer(request) : browserToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = tokens.verifiedSubject(token);
                // Recheck current role and enabled status on every request; stale JWT roles are never trusted.
                UserDetails details = users.loadUserByUsername(email);
                if (details.isEnabled() && details.isAccountNonLocked() && details.isAccountNonExpired()
                        && details.isCredentialsNonExpired()) {
                    var auth = UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException | org.springframework.security.core.AuthenticationException ignored) {
                // Invalid, expired, or revoked-by-account-state token: continue unauthenticated.
            }
        }
        chain.doFilter(request, response);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    private static String browserToken(HttpServletRequest request) {
        String bearer = bearer(request);
        if (bearer != null) return bearer;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (JwtCookieService.COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
