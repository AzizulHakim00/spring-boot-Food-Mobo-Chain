package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.ChangePasswordDTO;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.service.AgentMailService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountSecurityControllerTest {
    private final UserService users = mock(UserService.class);
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final JwtCookieService cookies = mock(JwtCookieService.class);
    private final AgentMailService mail = mock(AgentMailService.class);
    private final AccountSecurityController controller =
            new AccountSecurityController(users, manager, cookies, mail);

    private static ChangePasswordDTO form() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("OldPassword123");
        dto.setNewPassword("NewPassword456");
        dto.setConfirmPassword("NewPassword456");
        return dto;
    }

    @Test void existingVerifiedAccountRetainsEmailVerificationAndGetsNotice() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("buyer@example.test");
        when(manager.authenticate(any(Authentication.class))).thenReturn(auth);
        User buyer = User.builder().id("buyer-id").email("buyer@example.test")
                .emailVerified(true).enabled(true).build();
        when(users.getByEmail("buyer@example.test")).thenReturn(buyer);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ChangePasswordDTO dto = form();
        var errors = new BeanPropertyBindingResult(dto, "changePassword");

        String page = controller.update(auth, dto, errors, response,
                new RedirectAttributesModelMap());

        assertThat(page).isEqualTo("redirect:/login");
        assertThat(buyer.getEmailVerified()).isTrue();
        verify(users).changePassword(buyer, dto.getNewPassword());
        verify(mail).sendPasswordChanged("buyer@example.test");
        verify(cookies).clear(response);
    }

    @Test void demoLocalAccountRotatesPasswordWithoutSendingToUndeliverableAddress() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("seller1@foodmobo.local");
        when(manager.authenticate(any(Authentication.class))).thenReturn(auth);
        User seller = User.builder().id("users:3").email("seller1@foodmobo.local").build();
        when(users.getByEmail("seller1@foodmobo.local")).thenReturn(seller);
        ChangePasswordDTO dto = form();

        assertThat(controller.update(auth, dto,
                new BeanPropertyBindingResult(dto, "changePassword"),
                mock(HttpServletResponse.class), new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/login");
        verify(users).changePassword(seller, dto.getNewPassword());
        verifyNoInteractions(mail);
    }

    @Test void badCurrentPasswordDoesNotChangeCredentialsOrSendEmail() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("buyer@example.test");
        when(manager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("incorrect"));
        ChangePasswordDTO dto = form();
        var errors = new BeanPropertyBindingResult(dto, "changePassword");

        assertThat(controller.update(auth, dto, errors,
                mock(HttpServletResponse.class), new RedirectAttributesModelMap()))
                .isEqualTo("auth/change-password");
        assertThat(errors.hasFieldErrors("currentPassword")).isTrue();
        verifyNoInteractions(users, cookies, mail);
    }

    @Test void passwordChangeRemainsSuccessfulIfNoticeProviderIsDown() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("buyer@example.test");
        when(manager.authenticate(any(Authentication.class))).thenReturn(auth);
        User buyer = User.builder().id("buyer-id").email("buyer@example.test").build();
        when(users.getByEmail("buyer@example.test")).thenReturn(buyer);
        doThrow(new AgentMailService.MailUnavailableException("Unavailable"))
                .when(mail).sendPasswordChanged("buyer@example.test");
        ChangePasswordDTO dto = form();

        assertThat(controller.update(auth, dto,
                new BeanPropertyBindingResult(dto, "changePassword"),
                mock(HttpServletResponse.class), new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/login");
        verify(users).changePassword(buyer, dto.getNewPassword());
    }
}
