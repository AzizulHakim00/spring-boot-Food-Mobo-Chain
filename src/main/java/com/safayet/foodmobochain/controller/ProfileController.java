package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.ProfileDTO;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        User user = userService.getByEmail(authentication.getName());
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", userService.getProfile(user));
        }
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String update(Authentication authentication,
                         @Valid @ModelAttribute("profile") ProfileDTO dto,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/profile";
        }
        User user = userService.getByEmail(authentication.getName());
        userService.updateProfile(user, dto);
        redirectAttributes.addFlashAttribute("success", "Profile updated successfully.");
        return "redirect:/profile";
    }
}
