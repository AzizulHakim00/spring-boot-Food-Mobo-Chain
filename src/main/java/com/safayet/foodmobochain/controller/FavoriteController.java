package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.service.FavoriteService;
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
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;

    @GetMapping("/favorites")
    public String favorites(Authentication authentication, Model model) {
        User buyer = buyer(authentication);
        model.addAttribute("favoriteFoods", favoriteService.foods(buyer));
        model.addAttribute("favoriteCarts", favoriteService.carts(buyer));
        return "customer/favorite/favorites";
    }

    @PostMapping("/favorites/foods/{id}")
    public String toggleFood(Authentication authentication, @PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            boolean saved = favoriteService.toggleFood(buyer(authentication), id);
            redirectAttributes.addFlashAttribute("success", saved ? "Food saved to favorites." : "Food removed from favorites.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/favorites";
    }

    @PostMapping("/favorites/carts/{id}")
    public String toggleCart(Authentication authentication, @PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            boolean saved = favoriteService.toggleCart(buyer(authentication), id);
            redirectAttributes.addFlashAttribute("success", saved ? "Food cart saved to favorites." : "Food cart removed from favorites.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/favorites";
    }

    private User buyer(Authentication authentication) {
        return userService.getBuyerByEmail(authentication.getName());
    }
}
