package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.ForgotPasswordDTO;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import com.safayet.foodmobochain.dto.RegistrationDTO;
import com.safayet.foodmobochain.dto.ResetPasswordDTO;
import com.safayet.foodmobochain.dto.SellerRegistrationDTO;
import com.safayet.foodmobochain.service.PasswordResetService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtCookieService cookieService;

    @Value("${app.password-reset.log-demo-link:false}")
    private boolean demoResetLinksEnabled;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String username,
                              @RequestParam String password,
                              HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            cookieService.issue(response, jwtService.generateToken(authentication.getName()), jwtService.expiresInSeconds());
            boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean seller = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));
            return admin ? "redirect:/admin" : seller ? "redirect:/seller" : "redirect:/";
        } catch (AuthenticationException exception) {
            return "redirect:/login?error";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        cookieService.clear(response);
        return "redirect:/login?logout";
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registration")) {
            model.addAttribute("registration", new RegistrationDTO());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerBuyer(@Valid @ModelAttribute("registration") RegistrationDTO dto,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerBuyer(dto);
            redirectAttributes.addFlashAttribute("success", "Account created. You can sign in now.");
            return "redirect:/login";
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("registration", exception.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/seller/register")
    public String sellerRegister(Model model) {
        if (!model.containsAttribute("sellerRegistration")) {
            model.addAttribute("sellerRegistration", new SellerRegistrationDTO());
        }
        return "auth/seller-register";
    }

    @PostMapping("/seller/register")
    public String sellerRegister(@Valid @ModelAttribute("sellerRegistration") SellerRegistrationDTO dto,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/seller-register";
        }
        try {
            userService.registerSeller(dto);
            redirectAttributes.addFlashAttribute("success", "Seller account created. Your food cart is awaiting admin approval.");
            return "redirect:/login";
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("sellerRegistration", exception.getMessage());
            return "auth/seller-register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        if (!model.containsAttribute("forgotPassword")) {
            model.addAttribute("forgotPassword", new ForgotPasswordDTO());
        }
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPassword") ForgotPasswordDTO dto,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }
        passwordResetService.requestReset(dto.getEmail());
        redirectAttributes.addFlashAttribute("success", demoResetLinksEnabled
                ? "If that email exists, a development reset link has been logged to the local application console."
                : "If that email exists, the request was recorded. Email delivery is not yet enabled on this demo site.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam String token, Model model) {
        if (!passwordResetService.isTokenValid(token)) {
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
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }
        try {
            passwordResetService.resetPassword(dto.getToken(), dto.getPassword());
            redirectAttributes.addFlashAttribute("success", "Password changed. Sign in with your new password.");
            return "redirect:/login";
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("resetPassword", exception.getMessage());
            return "auth/reset-password";
        }
    }
}
