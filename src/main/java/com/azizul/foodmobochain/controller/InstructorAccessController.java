package com.azizul.foodmobochain.controller;

import com.azizul.foodmobochain.security.JwtCookieService;
import com.azizul.foodmobochain.service.AgentMailService;
import com.azizul.foodmobochain.service.InstructorAccessService;
import com.azizul.foodmobochain.service.InstructorAccessService.InstructorSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Instructor access is issued by an authenticated admin and confirmed by the mailbox owner. */
@Controller
@RequiredArgsConstructor
public class InstructorAccessController {
    private final InstructorAccessService access;
    private final JwtCookieService cookies;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/admin/instructor-access")
    public String manage(Model model) {
        if (!access.enabledForStaging()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("instructorEmailConfigured", true);
        return "instructor/manage";
    }

    @PostMapping("/admin/instructor-access/invite")
    public String invite(@RequestParam("email") String email,
                         @RequestParam("currentPassword") String currentPassword,
                         Authentication authentication,
                         RedirectAttributes redirects) {
        if (!access.enabledForStaging()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                    .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        final String recipient;
        try {
            recipient = InstructorAccessService.normalizeEmail(email);
        } catch (IllegalArgumentException invalid) {
            redirects.addFlashAttribute("error", invalid.getMessage());
            return "redirect:/admin/instructor-access";
        }

        if (currentPassword == null || currentPassword.isBlank() || currentPassword.length() > 256) {
            redirects.addFlashAttribute("error", "Enter your current Admin password to confirm.");
            return "redirect:/admin/instructor-access";
        }
        try {
            Authentication verified = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authentication.getName(), currentPassword));
            if (!verified.isAuthenticated() || verified.getAuthorities().stream()
                    .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } catch (AuthenticationException invalid) {
            redirects.addFlashAttribute("error", "Your Admin password could not be confirmed.");
            return "redirect:/admin/instructor-access";
        }
        try {
            access.sendInvitation(recipient, authentication.getName());
            redirects.addFlashAttribute("success",
                    "Invitation sent. The recipient's private link expires in 15 minutes.");
        } catch (IllegalArgumentException | IllegalStateException
                 | AgentMailService.MailUnavailableException unavailable) {
            redirects.addFlashAttribute("error", unavailable.getMessage());
        }
        return "redirect:/admin/instructor-access";
    }

    @PostMapping("/admin/instructor-access/revoke")
    public String revokeAll(RedirectAttributes redirects) {
        if (!access.enabledForStaging()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        access.revokeAll();
        redirects.addFlashAttribute("success", "All instructor invitations and active access have been revoked.");
        return "redirect:/admin/instructor-access";
    }

    @GetMapping("/instructor/accept")
    public String preview(@RequestParam(required = false) String token,
                          Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        boolean valid = access.validInvitation(token);
        model.addAttribute("valid", valid);
        if (valid) model.addAttribute("token", token);
        return "instructor/accept";
    }

    @PostMapping("/instructor/accept")
    public String accept(@RequestParam String token, HttpServletResponse response,
                         RedirectAttributes redirects) {
        if (!access.enabledForStaging()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            String rawSession = access.accept(token);
            // A normal browser login MUST NOT outlive a shorter instructor session.
            cookies.clear(response);
            access.setCookie(response, rawSession);
            return "redirect:/instructor/controls";
        } catch (IllegalArgumentException invalid) {
            redirects.addFlashAttribute("error", "Invitation invalid, expired or already used.");
            return "redirect:/login";
        }
    }

    @GetMapping("/instructor/controls")
    public String controls(HttpServletRequest request, Model model) {
        InstructorSession session = requireSession(request);
        model.addAttribute("instructorRole", session.role());
        model.addAttribute("instructorSlot", session.sellerSlot());
        model.addAttribute("instructorStores", InstructorAccessService.STORES);
        model.addAttribute("instructorExpiresAt", session.expiresAt());
        return "instructor/controls";
    }

    @PostMapping("/instructor/switch")
    public String switchRole(HttpServletRequest request,
                             @RequestParam String role,
                             @RequestParam(required = false) String store,
                             RedirectAttributes redirects) {
        requireSession(request);
        try {
            InstructorSession updated = access.switchRole(
                    InstructorAccessService.cookieValue(request), role, store);
            return switch (updated.role()) {
                case "admin" -> "redirect:/admin";
                case "seller" -> "redirect:/seller";
                default -> "redirect:/";
            };
        } catch (IllegalArgumentException invalid) {
            redirects.addFlashAttribute("error", invalid.getMessage());
            return "redirect:/instructor/controls";
        }
    }

    @PostMapping("/instructor/exit")
    public String exit(HttpServletRequest request, HttpServletResponse response) {
        access.revoke(InstructorAccessService.cookieValue(request));
        access.clearCookie(response);
        cookies.clear(response);
        return "redirect:/login?logout";
    }

    private static InstructorSession requireSession(HttpServletRequest request) {
        Object session = request.getAttribute(InstructorAccessService.REQUEST_ATTRIBUTE);
        if (!(session instanceof InstructorSession active)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor invitation required");
        }
        return active;
    }
}
