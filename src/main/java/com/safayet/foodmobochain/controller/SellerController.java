package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.FoodCartDTO;
import com.safayet.foodmobochain.dto.FoodItemDTO;
import com.safayet.foodmobochain.model.CustomerOrder;
import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.OrderStatus;
import com.safayet.foodmobochain.service.CatalogService;
import com.safayet.foodmobochain.service.OrderService;
import com.safayet.foodmobochain.service.ReportService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SellerController {

    private final UserService userService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final ReportService reportService;

    @GetMapping("/seller")
    public String dashboard(Authentication authentication, Model model) {
        User seller = seller(authentication);
        FoodCart cart = catalogService.sellerCart(seller);
        model.addAttribute("foodCart", cart);
        model.addAttribute("stats", reportService.sellerDashboard(cart));
        model.addAttribute("orders", orderService.sellerOrders(seller).stream().limit(6).toList());
        return "seller/dashboard";
    }

    @GetMapping("/seller/food-cart")
    public String foodCart(Authentication authentication, Model model) {
        User seller = seller(authentication);
        model.addAttribute("foodCart", catalogService.sellerCart(seller));
        if (!model.containsAttribute("foodCartForm")) {
            model.addAttribute("foodCartForm", catalogService.sellerCartDto(seller));
        }
        return "seller/food-cart";
    }

    @PostMapping("/seller/food-cart")
    public String updateFoodCart(Authentication authentication,
                                 @Valid @ModelAttribute("foodCartForm") FoodCartDTO dto,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        User seller = seller(authentication);
        if (bindingResult.hasErrors()) {
            model.addAttribute("foodCart", catalogService.sellerCart(seller));
            return "seller/food-cart";
        }
        catalogService.updateSellerCart(seller, dto);
        redirectAttributes.addFlashAttribute("success", "Food cart details updated.");
        return "redirect:/seller/food-cart";
    }

    @PostMapping("/seller/food-cart/toggle-open")
    public String toggleOpen(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            catalogService.toggleSellerCartOpen(seller(authentication));
            redirectAttributes.addFlashAttribute("success", "Food cart availability updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/seller";
    }

    @GetMapping("/seller/menu")
    public String menu(Authentication authentication, Model model) {
        model.addAttribute("menu", catalogService.sellerMenu(seller(authentication)));
        return "seller/menu";
    }

    @GetMapping("/seller/menu/new")
    public String newFood(Model model) {
        if (!model.containsAttribute("foodForm")) {
            model.addAttribute("foodForm", new FoodItemDTO());
        }
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("editing", false);
        return "seller/food-form";
    }

    @PostMapping("/seller/menu/new")
    public String createFood(Authentication authentication,
                             @Valid @ModelAttribute("foodForm") FoodItemDTO dto,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", catalogService.categories());
            model.addAttribute("editing", false);
            return "seller/food-form";
        }
        catalogService.createFood(seller(authentication), dto);
        redirectAttributes.addFlashAttribute("success", "Food item added to your menu.");
        return "redirect:/seller/menu";
    }

    @GetMapping("/seller/menu/{id}/edit")
    public String editFood(Authentication authentication, @PathVariable String id, Model model) {
        model.addAttribute("foodForm", catalogService.sellerFoodDto(seller(authentication), id));
        model.addAttribute("foodId", id);
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("editing", true);
        return "seller/food-form";
    }

    @PostMapping("/seller/menu/{id}/edit")
    public String updateFood(Authentication authentication,
                             @PathVariable String id,
                             @Valid @ModelAttribute("foodForm") FoodItemDTO dto,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("foodId", id);
            model.addAttribute("categories", catalogService.categories());
            model.addAttribute("editing", true);
            return "seller/food-form";
        }
        catalogService.updateFood(seller(authentication), id, dto);
        redirectAttributes.addFlashAttribute("success", "Food item updated.");
        return "redirect:/seller/menu";
    }

    @PostMapping("/seller/menu/{id}/toggle")
    public String toggleFood(Authentication authentication,
                             @PathVariable String id,
                             RedirectAttributes redirectAttributes) {
        catalogService.toggleSellerFoodAvailability(seller(authentication), id);
        redirectAttributes.addFlashAttribute("success", "Food availability updated.");
        return "redirect:/seller/menu";
    }

    @PostMapping("/seller/menu/{id}/archive")
    public String archiveFood(Authentication authentication,
                              @PathVariable String id,
                              RedirectAttributes redirectAttributes) {
        catalogService.archiveFood(seller(authentication), id);
        redirectAttributes.addFlashAttribute("success", "Food item archived.");
        return "redirect:/seller/menu";
    }

    @GetMapping("/seller/orders")
    public String orders(Authentication authentication, Model model) {
        model.addAttribute("orders", orderService.sellerOrders(seller(authentication)));
        return "seller/orders";
    }

    @GetMapping("/seller/orders/{orderNumber}")
    public String order(Authentication authentication, @PathVariable String orderNumber, Model model) {
        CustomerOrder order = orderService.sellerOrder(seller(authentication), orderNumber);
        model.addAttribute("order", order);
        model.addAttribute("payment", orderService.paymentFor(order));
        model.addAttribute("delivery", orderService.deliveryFor(order));
        model.addAttribute("allowedNext", orderService.allowedNext(order.getStatus()));
        return "seller/order-detail";
    }

    @PostMapping("/seller/orders/{orderNumber}/status")
    public String updateOrderStatus(Authentication authentication,
                                    @PathVariable String orderNumber,
                                    @RequestParam OrderStatus status,
                                    RedirectAttributes redirectAttributes) {
        try {
            orderService.updateStatusBySeller(seller(authentication), orderNumber, status);
            redirectAttributes.addFlashAttribute("success", "Order status updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/seller/orders/" + orderNumber;
    }

    private User seller(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }
}
