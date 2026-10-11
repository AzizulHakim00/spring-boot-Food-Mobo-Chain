package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.ChangePasswordDTO;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** All signed-in roles can securely rotate their own password without email. */
@Controller
@RequiredArgsConstructor
public class AccountSecurityController {
    private final UserService users;
    private final AuthenticationManager authenticationManager;
    private final JwtCookieService cookies;

    @GetMapping("/account/password")
    public String page(Model model) {
        if (!model.containsAttribute("changePassword")) {
            model.addAttribute("changePassword", new ChangePasswordDTO());
        }
        return "auth/change-password";
    }

    @PostMapping("/account/password")
    public String update(Authentication authentication,
                         @Valid @ModelAttribute("changePassword") ChangePasswordDTO dto,
                         BindingResult errors, HttpServletResponse response,
                         RedirectAttributes redirects) {
        if (errors.hasErrors()) return "auth/change-password";
        if (dto.getCurrentPassword().equals(dto.getNewPassword())) {
            errors.rejectValue("newPassword", "same", "Choose a password different from the current one.");
            return "auth/change-password";
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    authentication.getName(), dto.getCurrentPassword()));
        } catch (AuthenticationException invalid) {
            errors.rejectValue("currentPassword", "invalid", "Current password is incorrect.");
            return "auth/change-password";
        }

        users.changePassword(users.getByEmail(authentication.getName()), dto.getNewPassword());
        cookies.clear(response);
        redirects.addFlashAttribute("success", "Password changed. Sign in again using the new password.");
        return "redirect:/login";
    }
}
