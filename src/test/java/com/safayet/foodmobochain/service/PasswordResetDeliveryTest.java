package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetDeliveryTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AgentMailService mail = mock(AgentMailService.class);
    private final PasswordResetService service = new PasswordResetService(users, tokens, encoder, mail);

    @Test void existingAccountReceivesOneTimeThirtyMinuteResetLink() {
        User buyer = User.builder().id("users:2").email("buyer@example.test").build();
        when(users.findByEmailNormalized(buyer.getEmail())).thenReturn(Optional.of(buyer));
        service.requestReset(buyer.getEmail());
        var stored = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        var raw = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tokens).save(stored.capture());
        verify(mail).sendPasswordReset(eq(buyer.getEmail()), raw.capture());
        assertThat(stored.getValue().getTokenHash())
                .isEqualTo(com.safayet.foodmobochain.security.AccountTokens.hash(raw.getValue()));
        assertThat(stored.getValue().getExpiresAt()).isAfter(stored.getValue().getLastSentAt().plusMinutes(29));
    }

    @Test void missingAccountIsNotDisclosedOrEmailed() {
        when(users.findByEmailNormalized("missing@example.test")).thenReturn(Optional.empty());
        service.requestReset("missing@example.test");
        verifyNoInteractions(tokens);
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test void resendWithinNinetySecondsDoesNotGenerateAnotherToken() {
        User buyer = User.builder().id("users:2").email("buyer@example.test").build();
        PasswordResetToken recent = PasswordResetToken.builder().userId(buyer.getId())
                .lastSentAt(java.time.LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE)
                        .minusSeconds(10)).build();
        when(users.findByEmailNormalized(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(tokens.findByUserId(buyer.getId())).thenReturn(Optional.of(recent));
        service.requestReset(buyer.getEmail());
        verify(tokens, never()).save(any());
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
    }
}
