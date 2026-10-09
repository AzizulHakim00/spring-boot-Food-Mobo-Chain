package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.service.CatalogService;
import com.safayet.foodmobochain.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CatalogService catalogService;
    private final DiscountService discountService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredFoods", catalogService.featuredFoods());
        model.addAttribute("foodCarts", catalogService.carts().stream().limit(6).toList());
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("offers", discountService.activeDiscounts().stream().limit(3).toList());
        return "customer/home";
    }
}
