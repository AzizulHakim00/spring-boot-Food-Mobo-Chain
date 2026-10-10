package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.Cart;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.SpiceLevel;
import com.safayet.foodmobochain.service.CartService;
import com.safayet.foodmobochain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserService userService;

    @GetMapping("/cart")
    public String cart(Authentication authentication, Model model) {
        User buyer = buyer(authentication);
        Cart cart = cartService.getOrCreate(buyer);
        model.addAttribute("cart", cart);
        model.addAttribute("subtotal", cartService.subtotal(cart));
        model.addAttribute("vendorGroups", cartService.vendorGroups(cart));
        model.addAttribute("deliveryTotal", cartService.deliveryTotal(cart));
        model.addAttribute("spiceLevels", SpiceLevel.values());
        return "customer/cart/cart";
    }

    @PostMapping("/cart/add")
    public String add(Authentication authentication,
                      @RequestParam String foodId,
                      @RequestParam(defaultValue = "1") int quantity,
                      @RequestParam(defaultValue = "REGULAR") SpiceLevel spiceLevel,
                      RedirectAttributes redirectAttributes) {
        try {
            cartService.add(buyer(authentication), foodId, quantity, spiceLevel);
            redirectAttributes.addFlashAttribute("success", "Added to your cart.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{id}/update")
    public String update(Authentication authentication,
                         @PathVariable String id,
                         @RequestParam int quantity,
                         @RequestParam SpiceLevel spiceLevel,
                         RedirectAttributes redirectAttributes) {
        try {
            cartService.update(buyer(authentication), id, quantity, spiceLevel);
            redirectAttributes.addFlashAttribute("success", "Cart updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{id}/remove")
    public String remove(Authentication authentication, @PathVariable String id, RedirectAttributes redirectAttributes) {
        cartService.remove(buyer(authentication), id);
        redirectAttributes.addFlashAttribute("success", "Item removed from cart.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clear(Authentication authentication, RedirectAttributes redirectAttributes) {
        cartService.clear(buyer(authentication));
        redirectAttributes.addFlashAttribute("success", "Cart cleared.");
        return "redirect:/cart";
    }

    private User buyer(Authentication authentication) {
        return userService.getBuyerByEmail(authentication.getName());
    }
}
