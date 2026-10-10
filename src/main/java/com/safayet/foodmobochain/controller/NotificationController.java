package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.service.NotificationService;
import com.safayet.foodmobochain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/notifications")
    public String notifications(Authentication authentication, Model model) {
        model.addAttribute("notifications", notificationService.all(user(authentication)));
        return "customer/notification/notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(Authentication authentication, @PathVariable String id, RedirectAttributes redirectAttributes) {
        notificationService.markRead(user(authentication), id);
        redirectAttributes.addFlashAttribute("success", "Notification marked as read.");
        return "redirect:/notifications";
    }

    private User user(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }
}
