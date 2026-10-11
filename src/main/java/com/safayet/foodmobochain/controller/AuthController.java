package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.*;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.security.AccountUserDetails;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.security.JwtService;
import com.safayet.foodmobochain.service.EmailVerificationService;
import com.safayet.foodmobochain.service.PasswordResetService;
import com.safayet.foodmobochain.service.AgentMailService.MailUnavailableException;
import com.safayet.foodmobochain.service.UserService;
import com.safayet.foodmobochain.service.InstructorAccessService;
import jakarta.servlet.http.HttpServletRequest;
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

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserService users;
    private final PasswordResetService resets;
    private final EmailVerificationService verification;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtCookieService cookieService;
    private final InstructorAccessService instructor;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String username,
                              @RequestParam String password,
                              HttpServletResponse response,
                              HttpServletRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            instructor.revoke(InstructorAccessService.cookieValue(request));
            instructor.clearCookie(response);
            cookieService.issue(response,
                    jwtService.generateToken(auth.getName(),
                            ((AccountUserDetails) auth.getPrincipal()).sessionVersion()),
                    jwtService.expiresInSeconds());
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean seller = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));
            return admin ? "redirect:/admin" : seller ? "redirect:/seller" : "redirect:/";
        } catch (AuthenticationException invalid) {
            return "redirect:/login?error";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response, HttpServletRequest request) {
        instructor.revoke(InstructorAccessService.cookieValue(request));
        instructor.clearCookie(response);
        cookieService.clear(response);
        return "redirect:/login?logout";
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registration"))
            model.addAttribute("registration", new RegistrationDTO());
        model.addAttribute("mailReady", verification.isMailConfigured());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerBuyer(@Valid @ModelAttribute("registration") RegistrationDTO dto,
                                BindingResult errors, Model model, RedirectAttributes redirects) {
        model.addAttribute("mailReady", verification.isMailConfigured());
        if (errors.hasErrors()) return "auth/register";
        User buyer;
        try {
            verification.requireMailConfigured();
            buyer = users.registerBuyer(dto);
        } catch (MailUnavailableException | IllegalArgumentException invalid) {
            errors.reject("registration", invalid.getMessage());
            return "auth/register";
        }
        try {
            verification.sendForUser(buyer);
            redirects.addFlashAttribute("success", "Buyer account created. Check your inbox and verify your email before signing in.");
        } catch (MailUnavailableException unavailable) {
            redirects.addFlashAttribute("error", "Account saved, but email delivery failed. Use the resend option to try again.");
        }
        return "redirect:/verification/pending";
    }

    @GetMapping("/seller/register")
    public String sellerRegister(Model model) {
        if (!model.containsAttribute("sellerRegistration"))
            model.addAttribute("sellerRegistration", new SellerRegistrationDTO());
        model.addAttribute("mailReady", verification.isMailConfigured());
        return "auth/seller-register";
    }

    @PostMapping("/seller/register")
    public String sellerRegister(@Valid @ModelAttribute("sellerRegistration") SellerRegistrationDTO dto,
                                 BindingResult errors, Model model, RedirectAttributes redirects) {
        model.addAttribute("mailReady", verification.isMailConfigured());
        if (errors.hasErrors()) return "auth/seller-register";
        User seller;
        try {
            verification.requireMailConfigured();
            seller = users.registerSeller(dto);
        } catch (MailUnavailableException | IllegalArgumentException invalid) {
            errors.reject("sellerRegistration", invalid.getMessage());
            return "auth/seller-register";
        }
        try {
            verification.sendForUser(seller);
            redirects.addFlashAttribute("success", "Seller account created. Verify your email first; your food cart also requires admin approval.");
        } catch (MailUnavailableException unavailable) {
            redirects.addFlashAttribute("error", "Account saved, but email delivery failed. Use the resend option to try again.");
        }
        return "redirect:/verification/pending";
    }

    @GetMapping({"/verification/pending", "/verification/resend"})
    public String resendPage(Model model) {
        model.addAttribute("mailReady", verification.isMailConfigured());
        return "auth/resend-verification";
    }

    @PostMapping("/verification/resend")
    public String resend(@RequestParam String email, RedirectAttributes redirects) {
        try {
            verification.resend(email);
            redirects.addFlashAttribute("success",
                    "If this email belongs to an unverified account, a verification link has been requested.");
        } catch (MailUnavailableException unavailable) {
            redirects.addFlashAttribute("error", "Email delivery is unavailable. Please try again later.");
        }
        return "redirect:/verification/pending";
    }

    /** Link preview never verifies automatically: user confirms via CSRF-protected POST. */
    @GetMapping("/verify-email")
    public String verificationPage(@RequestParam(required = false) String token, Model model) {
        boolean valid = verification.isValid(token);
        model.addAttribute("invalidToken", !valid);
        if (valid) model.addAttribute("token", token);
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    public String verify(@RequestParam String token, RedirectAttributes redirects) {
        try {
            verification.confirm(token);
            redirects.addFlashAttribute("success", "Email verified! You can sign in now.");
            return "redirect:/login";
        } catch (IllegalArgumentException invalid) {
            redirects.addFlashAttribute("error", invalid.getMessage());
            return "redirect:/verification/pending";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        if (!model.containsAttribute("forgotPassword"))
            model.addAttribute("forgotPassword", new ForgotPasswordDTO());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPassword") ForgotPasswordDTO dto,
                                 BindingResult errors, RedirectAttributes redirects) {
        if (errors.hasErrors()) return "auth/forgot-password";
        try {
            resets.requestReset(dto.getEmail());
            redirects.addFlashAttribute("success",
                    "If this address belongs to an account, a password-reset link has been requested.");
        } catch (MailUnavailableException unavailable) {
            redirects.addFlashAttribute("error", "Email delivery is unavailable. Please try again later.");
        }
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token, Model model) {
        if (!resets.isTokenValid(token)) {
            model.addAttribute("invalidToken", true);
            return "auth/reset-password";
        }
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setToken(token);
        model.addAttribute("resetPassword", dto);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPassword") ResetPasswordDTO dto,
                                BindingResult errors, RedirectAttributes redirects) {
        if (errors.hasErrors()) return "auth/reset-password";
        try {
            resets.resetPassword(dto.getToken(), dto.getPassword());
            redirects.addFlashAttribute("success", "Password changed. Sign in with your new password.");
            return "redirect:/login";
        } catch (IllegalArgumentException invalid) {
            errors.reject("resetPassword", invalid.getMessage());
            return "auth/reset-password";
        }
    }
}
