package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.ReviewDTO;
import com.safayet.foodmobochain.service.ReviewService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @PostMapping("/reviews")
    public String review(Authentication authentication,
                         @Valid @ModelAttribute ReviewDTO review,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please provide a rating and a valid review.");
            return "redirect:/orders";
        }
        try {
            reviewService.submit(userService.getBuyerByEmail(authentication.getName()), review);
            redirectAttributes.addFlashAttribute("success", "Review submitted for moderation. Thank you!");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/orders";
    }
}
