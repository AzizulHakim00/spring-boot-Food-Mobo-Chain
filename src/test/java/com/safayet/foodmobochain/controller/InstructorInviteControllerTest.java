package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.service.InstructorAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstructorInviteControllerTest {
    private final InstructorAccessService invites = mock(InstructorAccessService.class);
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final InstructorAccessController controller =
            new InstructorAccessController(invites, mock(JwtCookieService.class), manager);

    private static Authentication admin() {
        return UsernamePasswordAuthenticationToken.authenticated("admin@foodmobo.local", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test void realAdminCanSendToTypedEmailAfterVerifyingCurrentPassword() {
        when(invites.enabledForStaging()).thenReturn(true);
        when(manager.authenticate(any(Authentication.class))).thenReturn(admin());
        var flash = new RedirectAttributesModelMap();
        String route = controller.invite("  Noor@Seu.Edu.Bd  ", "current-admin-password", admin(), flash);

        assertThat(route).isEqualTo("redirect:/admin/instructor-access");
        verify(manager).authenticate(any(Authentication.class));
        verify(invites).sendInvitation("noor@seu.edu.bd", "admin@foodmobo.local");
        assertThat(flash.getFlashAttributes().get("success")).asString().contains("Invitation sent");
    }

    @Test void incorrectAdminPasswordCannotSendInvitation() {
        when(invites.enabledForStaging()).thenReturn(true);
        when(manager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad password"));
        var flash = new RedirectAttributesModelMap();

        assertThat(controller.invite("recipient@seu.edu.bd", "bad-password", admin(), flash))
                .isEqualTo("redirect:/admin/instructor-access");
        assertThat(flash.getFlashAttributes().get("error")).isNotNull();
        verify(invites, never()).sendInvitation(anyString(), anyString());
    }

    @Test void invalidRecipientNeverRequestsPasswordVerificationOrEmail() {
        when(invites.enabledForStaging()).thenReturn(true);
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.invite("not-an-email", "current-admin-password", admin(), flash))
                .isEqualTo("redirect:/admin/instructor-access");
        verifyNoInteractions(manager);
        verify(invites, never()).sendInvitation(anyString(), anyString());
    }

    @Test void instructorOrBuyerCannotIssueNewInvitations() {
        when(invites.enabledForStaging()).thenReturn(true);
        Authentication buyer = UsernamePasswordAuthenticationToken.authenticated("buyer@foodmobo.local",
                null, List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        assertThatThrownBy(() -> controller.invite("recipient@seu.edu.bd", "anything",
                buyer, new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(manager);
        verify(invites, never()).sendInvitation(anyString(), anyString());
    }

    @Test void blankPasswordCannotSendAnInvitation() {
        when(invites.enabledForStaging()).thenReturn(true);
        assertThat(controller.invite("recipient@seu.edu.bd", "",
                admin(), new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/admin/instructor-access");
        verifyNoInteractions(manager);
        verify(invites, never()).sendInvitation(anyString(), anyString());
    }
}
