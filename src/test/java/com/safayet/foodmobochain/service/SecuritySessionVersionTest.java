package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.config.MongoConfig;
import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SecuritySessionVersionTest {

    @Test
    void successfulPasswordResetInvalidatesEarlierSessions() {
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordResetService resets = new PasswordResetService(users, tokens, encoder, mock(AgentMailService.class));
        User buyer = User.builder().id("users:buyer").password("previous-hash")
                .sessionVersion(4L).role(Role.BUYER).enabled(true).build();
        PasswordResetToken reset = PasswordResetToken.builder()
                .userId(buyer.getId())
                .expiresAt(LocalDateTime.now(MongoConfig.APP_ZONE).plusMinutes(10))
                .used(false).build();
        when(tokens.findByTokenHashAndUsedFalse(anyString())).thenReturn(Optional.of(reset));
        when(users.findById(buyer.getId())).thenReturn(Optional.of(buyer));
        when(encoder.encode("a-new-password")).thenReturn("new-hash");

        resets.resetPassword(com.safayet.foodmobochain.security.AccountTokens.create(), "a-new-password");

        assertThat(buyer.getPassword()).isEqualTo("new-hash");
        assertThat(buyer.getSessionVersion()).isEqualTo(5L);
        assertThat(reset.isUsed()).isTrue();
        verify(users).save(buyer);
        verify(tokens).save(reset);
    }

    @Test
    void suspendingAnAccountRevokesPreviousJwtVersion() {
        UserRepository users = mock(UserRepository.class);
        FoodCartRepository carts = mock(FoodCartRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService(users, carts, encoder);
        User buyer = User.builder().id("users:buyer").role(Role.BUYER)
                .enabled(true).sessionVersion(0L).build();
        when(users.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        service.toggleEnabled(buyer.getId());

        assertThat(buyer.isEnabled()).isFalse();
        assertThat(buyer.getSessionVersion()).isEqualTo(1L);
        verify(users).save(buyer);
        verifyNoInteractions(carts);
    }
}
