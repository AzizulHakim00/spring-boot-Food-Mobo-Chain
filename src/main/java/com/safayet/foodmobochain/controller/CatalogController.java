package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.FoodItem;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.service.CatalogService;
import com.safayet.foodmobochain.service.FavoriteService;
import com.safayet.foodmobochain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final FavoriteService favoriteService;
    private final UserService userService;

    @GetMapping("/foods")
    public String foods(@RequestParam(required = false) String q,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String cart,
                        @RequestParam(required = false) BigDecimal min,
                        @RequestParam(required = false) BigDecimal max,
                        @RequestParam(defaultValue = "popular") String sort,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Page<FoodItem> foods = catalogService.searchFoods(q, category, cart, min, max, sort, page, 12);
        model.addAttribute("foods", foods);
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("foodCarts", catalogService.carts());
        model.addAttribute("q", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedCart", cart);
        model.addAttribute("min", min);
        model.addAttribute("max", max);
        model.addAttribute("sort", sort);
        return "customer/catalog/foods";
    }

    @GetMapping("/foods/{id}")
    public String food(@PathVariable String id, Authentication authentication, Model model) {
        FoodItem food = catalogService.food(id);
        model.addAttribute("food", food);
        model.addAttribute("reviews", catalogService.foodReviews(food));
        model.addAttribute("rating", catalogService.foodRating(food));
        User buyer = buyer(authentication);
        model.addAttribute("favorite", buyer != null && favoriteService.isFoodFavorite(buyer, food));
        return "customer/catalog/food-detail";
    }

    @GetMapping("/food-carts")
    public String foodCarts(Model model) {
        model.addAttribute("foodCarts", catalogService.carts());
        return "customer/catalog/food-carts";
    }

    @GetMapping("/food-carts/{slug}")
    public String foodCart(@PathVariable String slug, Authentication authentication, Model model) {
        FoodCart cart = catalogService.cart(slug);
        model.addAttribute("foodCart", cart);
        model.addAttribute("menu", catalogService.menu(cart));
        model.addAttribute("reviews", catalogService.cartReviews(cart));
        model.addAttribute("rating", catalogService.cartRating(cart));
        User buyer = buyer(authentication);
        model.addAttribute("favorite", buyer != null && favoriteService.isCartFavorite(buyer, cart));
        return "customer/catalog/food-cart-detail";
    }

    private User buyer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        User user = userService.getByEmail(authentication.getName());
        return user.getRole() == Role.BUYER ? user : null;
    }
}
