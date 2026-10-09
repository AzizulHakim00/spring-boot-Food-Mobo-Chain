package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.DiscountDTO;
import com.safayet.foodmobochain.model.enums.DiscountType;
import com.safayet.foodmobochain.model.enums.NotificationType;
import com.safayet.foodmobochain.service.CatalogService;
import com.safayet.foodmobochain.service.DiscountService;
import com.safayet.foodmobochain.service.NotificationService;
import com.safayet.foodmobochain.service.OrderService;
import com.safayet.foodmobochain.service.PaymentService;
import com.safayet.foodmobochain.service.ReportService;
import com.safayet.foodmobochain.service.ReviewService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import com.safayet.foodmobochain.service.CloudinaryImageService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final DiscountService discountService;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final ReportService reportService;
    private final CloudinaryImageService imageService;

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("stats", reportService.adminDashboard());
        model.addAttribute("recentOrders", orderService.recentOrders().stream().limit(8).toList());
        model.addAttribute("buyerCount", reportService.buyerCount());
        model.addAttribute("sellerCount", reportService.sellerCount());
        return "admin/dashboard";
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/toggle")
    public String toggleUser(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleEnabled(id);
            redirectAttributes.addFlashAttribute("success", "User access updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/food-carts")
    public String foodCarts(Model model) {
        model.addAttribute("foodCarts", catalogService.allCarts());
        return "admin/food-carts";
    }

    @PostMapping("/admin/food-carts/{id}/approval")
    public String cartApproval(@PathVariable String id, RedirectAttributes redirectAttributes) {
        catalogService.toggleCartApproval(id);
        redirectAttributes.addFlashAttribute("success", "Food cart approval status updated.");
        return "redirect:/admin/food-carts";
    }

    @GetMapping("/admin/foods")
    public String foods(Model model) {
        model.addAttribute("foods", catalogService.allFoods());
        return "admin/foods";
    }

    @PostMapping("/admin/foods/{id}/price")
    public String updateFoodPrice(@PathVariable String id,
                                  @RequestParam BigDecimal price,
                                  RedirectAttributes redirectAttributes) {
        try {
            catalogService.updateFoodPrice(id, price);
            redirectAttributes.addFlashAttribute("success", "Food price updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/foods";
    }

    @PostMapping("/admin/foods/{id}/availability")
    public String toggleFoodAvailability(@PathVariable String id, RedirectAttributes redirectAttributes) {
        catalogService.toggleFoodAvailability(id);
        redirectAttributes.addFlashAttribute("success", "Food availability updated.");
        return "redirect:/admin/foods";
    }

    @PostMapping("/admin/foods/{id}/featured")
    public String toggleFoodFeatured(@PathVariable String id, RedirectAttributes redirectAttributes) {
        catalogService.toggleFoodFeatured(id);
        redirectAttributes.addFlashAttribute("success", "Featured status updated.");
        return "redirect:/admin/foods";
    }

    @PostMapping("/admin/foods/{id}/archive")
    public String toggleFoodArchived(@PathVariable String id, RedirectAttributes redirectAttributes) {
        catalogService.toggleFoodArchived(id);
        redirectAttributes.addFlashAttribute("success", "Food archive status updated.");
        return "redirect:/admin/foods";
    }

    @GetMapping("/admin/categories")
    public String categories(Model model) {
        model.addAttribute("categories", catalogService.allCategories());
        return "admin/categories";
    }

    @PostMapping("/admin/categories")
    public String createCategory(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            catalogService.createCategory(name);
            redirectAttributes.addFlashAttribute("success", "Category created.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/image")
    public String categoryImage(@PathVariable String id,
                                @RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        try {
            String url = imageService.upload(file, "categories");
            catalogService.updateCategoryImage(id, url);
            redirectAttributes.addFlashAttribute("success", "Category image updated.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/toggle")
    public String toggleCategory(@PathVariable String id, RedirectAttributes redirectAttributes) {
        catalogService.toggleCategory(id);
        redirectAttributes.addFlashAttribute("success", "Category status updated.");
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderService.recentOrders());
        return "admin/orders";
    }

    @GetMapping("/admin/orders/{orderNumber}")
    public String order(@PathVariable String orderNumber, Model model) {
        var order = orderService.getByNumber(orderNumber);
        model.addAttribute("order", order);
        model.addAttribute("payment", orderService.paymentFor(order));
        model.addAttribute("delivery", orderService.deliveryFor(order));
        return "admin/order-detail";
    }

    @PostMapping("/admin/orders/{orderNumber}/cancel")
    public String cancelOrder(@PathVariable String orderNumber, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelByAdmin(orderNumber);
            redirectAttributes.addFlashAttribute("success", "Order cancelled by administrator.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/orders/" + orderNumber;
    }

    @GetMapping("/admin/payments")
    public String payments(Model model) {
        model.addAttribute("payments", paymentService.recentPayments());
        return "admin/payments";
    }

    @GetMapping("/admin/deliveries")
    public String deliveries(Model model) {
        model.addAttribute("deliveries", orderService.recentDeliveries());
        return "admin/deliveries";
    }

    @GetMapping("/admin/discounts")
    public String discounts(Model model) {
        model.addAttribute("discounts", discountService.allDiscounts());
        if (!model.containsAttribute("discountForm")) {
            model.addAttribute("discountForm", new DiscountDTO());
        }
        model.addAttribute("discountTypes", DiscountType.values());
        return "admin/discounts";
    }

    @PostMapping("/admin/discounts")
    public String createDiscount(@Valid @ModelAttribute("discountForm") DiscountDTO dto,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("discounts", discountService.allDiscounts());
            model.addAttribute("discountTypes", DiscountType.values());
            return "admin/discounts";
        }
        try {
            discountService.create(dto);
            redirectAttributes.addFlashAttribute("success", "Discount created.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/discounts";
    }

    @PostMapping("/admin/discounts/{id}/toggle")
    public String toggleDiscount(@PathVariable String id, RedirectAttributes redirectAttributes) {
        discountService.toggle(id);
        redirectAttributes.addFlashAttribute("success", "Discount status updated.");
        return "redirect:/admin/discounts";
    }

    @GetMapping("/admin/reviews")
    public String reviews(Model model) {
        model.addAttribute("reviews", reviewService.all());
        model.addAttribute("pendingReviews", reviewService.pending().size());
        return "admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/approve")
    public String approveReview(@PathVariable String id, RedirectAttributes redirectAttributes) {
        reviewService.approve(id);
        redirectAttributes.addFlashAttribute("success", "Review approved.");
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/hide")
    public String hideReview(@PathVariable String id, RedirectAttributes redirectAttributes) {
        reviewService.hide(id);
        redirectAttributes.addFlashAttribute("success", "Review hidden.");
        return "redirect:/admin/reviews";
    }

    @GetMapping("/admin/notifications")
    public String notifications() {
        return "admin/notifications";
    }

    @PostMapping("/admin/notifications")
    public String broadcast(@RequestParam NotificationType type,
                            @RequestParam String title,
                            @RequestParam String message,
                            @RequestParam(required = false) String link,
                            RedirectAttributes redirectAttributes) {
        try {
            int count = notificationService.broadcast(type, title, message, link);
            redirectAttributes.addFlashAttribute("success", "Notification sent to " + count + " active users.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    @GetMapping("/admin/reports")
    public String reports(Model model) {
        model.addAttribute("stats", reportService.adminDashboard());
        model.addAttribute("buyerCount", reportService.buyerCount());
        model.addAttribute("sellerCount", reportService.sellerCount());
        return "admin/reports";
    }
}
