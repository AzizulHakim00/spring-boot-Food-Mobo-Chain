package com.safayet.foodmobochain.config;

import com.safayet.foodmobochain.security.JwtAuthFilter;
import com.safayet.foodmobochain.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AccessDeniedHandlerImpl denied = new AccessDeniedHandlerImpl();
        denied.setErrorPage("/error/403");
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // MVC forms use CSRF tokens; /api/** only accepts Authorization headers, not JWT cookies.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/foods", "/foods/**", "/food-carts", "/food-carts/**",
                                "/login", "/register", "/seller/register", "/health", "/ready", "/api/auth/login",
                                "/forgot-password", "/reset-password",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller/**").hasRole("SELLER")
                        .requestMatchers("/cart/**", "/checkout/**", "/orders/**",
                                "/favorites/**", "/reviews/**", "/payment/**").hasRole("BUYER")
                        .requestMatchers("/profile/**", "/notifications/**", "/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(401, "Authentication required");
                            } else {
                                new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, exception);
                            }
                        })
                        // Log the HTTP method/path and exception type only. Never log JWTs,
                        // CSRF tokens, session cookies, submitted passwords, or query params.
                        .accessDeniedHandler((request, response, reason) -> {
                            log.warn("Rejected {} {}: {}",
                                    request.getMethod(), request.getRequestURI(),
                                    reason.getClass().getSimpleName());
                            denied.handle(request, response, reason);
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
