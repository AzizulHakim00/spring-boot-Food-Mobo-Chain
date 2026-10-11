package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.config.MongoConfig;
import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.repository.*;
import com.safayet.foodmobochain.security.AccountTokens;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {
    private final EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AgentMailService mail = mock(AgentMailService.class);
    private final EmailVerificationService service = new EmailVerificationService(tokens, users, mail);
    private final User buyer = User.builder().id("users:pending").email("pending@example.test")
            .emailVerified(false).enabled(true).sessionVersion(0L).build();

    @Test void registrationStoresOnlyHashAndSendsTheRawLinkByEmail() {
        when(tokens.findById("verification:" + buyer.getId())).thenReturn(Optional.empty());
        when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.sendForUser(buyer);

        var stored = org.mockito.ArgumentCaptor.forClass(EmailVerificationToken.class);
        var raw = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tokens).save(stored.capture());
        verify(mail).sendVerification(eq(buyer.getEmail()), raw.capture());
        assertThat(raw.getValue()).hasSize(43);
        assertThat(stored.getValue().getTokenHash()).isEqualTo(AccountTokens.hash(raw.getValue()));
        assertThat(stored.getValue().getTokenHash()).doesNotContain(raw.getValue());
        assertThat(stored.getValue().getExpiresAt())
                .isAfter(stored.getValue().getLastSentAt().plusHours(23));
    }

    @Test void linkConfirmationIsSingleUseAndActivatesAccount() {
        String raw = AccountTokens.create();
        EmailVerificationToken link = EmailVerificationToken.builder().userId(buyer.getId())
                .tokenHash(AccountTokens.hash(raw)).expiresAt(LocalDateTime.now(MongoConfig.APP_ZONE).plusHours(2)).build();
        when(tokens.findByTokenHashAndUsedFalse(AccountTokens.hash(raw)))
                .thenReturn(Optional.of(link), Optional.empty());
        when(users.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        assertThat(service.isValid(raw)).isTrue();
        // isValid consumes first mock result; configure an explicit result for confirmation.
        when(tokens.findByTokenHashAndUsedFalse(AccountTokens.hash(raw)))
                .thenReturn(Optional.of(link), Optional.empty());
        service.confirm(raw);
        assertThat(buyer.getEmailVerified()).isTrue();
        assertThat(buyer.getSessionVersion()).isOne();
        assertThat(link.isUsed()).isTrue();
        verify(users).save(buyer);
        assertThatThrownBy(() -> service.confirm(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void expiredTokensDoNotActivateAccounts() {
        String raw = AccountTokens.create();
        EmailVerificationToken expired = EmailVerificationToken.builder().userId(buyer.getId())
                .tokenHash(AccountTokens.hash(raw)).expiresAt(LocalDateTime.now(MongoConfig.APP_ZONE).minusMinutes(1)).build();
        when(tokens.findByTokenHashAndUsedFalse(AccountTokens.hash(raw))).thenReturn(Optional.of(expired));
        assertThat(service.isValid(raw)).isFalse();
        assertThatThrownBy(() -> service.confirm(raw)).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test void resendCooldownAvoidsMultipleMessages() {
        EmailVerificationToken recent = EmailVerificationToken.builder()
                .id("verification:" + buyer.getId())
                .lastSentAt(LocalDateTime.now(MongoConfig.APP_ZONE).minusSeconds(15)).build();
        when(tokens.findById(recent.getId())).thenReturn(Optional.of(recent));
        service.sendForUser(buyer);
        verify(mail, never()).sendVerification(anyString(), anyString());
        verify(tokens, never()).save(any());
    }

    @Test void alreadyVerifiedOrLegacyAccountsNeverReceiveVerificationLinks() {
        buyer.setEmailVerified(true);
        service.sendForUser(buyer);
        buyer.setEmailVerified(null);
        service.sendForUser(buyer);
        verify(tokens, never()).save(any());
        verify(mail, never()).sendVerification(anyString(), anyString());
    }

    @Test void invalidOrOversizedTokenIsRejectedWithoutDatabaseLookup() {
        assertThat(service.isValid("not-a-link")).isFalse();
        assertThat(service.isValid("A".repeat(500))).isFalse();
        assertThatThrownBy(() -> service.confirm("wrong")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tokens);
    }
}
