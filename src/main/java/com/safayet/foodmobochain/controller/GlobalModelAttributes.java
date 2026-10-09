package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.service.CartService;
import com.safayet.foodmobochain.service.NotificationService;
import com.safayet.foodmobochain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final UserService userService;
    private final CartService cartService;
    private final NotificationService notificationService;

    @ModelAttribute
    public void addSharedData(Model model, Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            model.addAttribute("cartCount", 0);
            model.addAttribute("unreadNotificationCount", 0);
            return;
        }

        User user = userService.getByEmail(authentication.getName());
        model.addAttribute("currentUser", user);
        model.addAttribute("cartCount", user.getRole() == Role.BUYER ? cartService.itemCount(user) : 0);
        model.addAttribute("unreadNotificationCount", notificationService.unreadCount(user));
    }
}
