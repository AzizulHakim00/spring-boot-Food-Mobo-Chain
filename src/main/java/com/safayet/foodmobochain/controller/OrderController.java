package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.CheckoutDTO;
import com.safayet.foodmobochain.model.Cart;
import com.safayet.foodmobochain.model.CustomerOrder;
import com.safayet.foodmobochain.model.Payment;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.PaymentMethod;
import com.safayet.foodmobochain.service.CartService;
import com.safayet.foodmobochain.service.DiscountService;
import com.safayet.foodmobochain.service.OrderService;
import com.safayet.foodmobochain.service.PaymentService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class OrderController {

    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final DiscountService discountService;

    @GetMapping("/checkout")
    public String checkout(Authentication authentication, Model model) {
        User buyer = buyer(authentication);
        Cart cart = cartService.getOrCreate(buyer);
        if (cart.getItems().isEmpty()) {
            return "redirect:/cart";
        }
        if (!model.containsAttribute("checkout")) {
            CheckoutDTO dto = new CheckoutDTO();
            dto.setDeliveryAddress(buyer.getAddress());
            dto.setPhone(buyer.getPhone());
            dto.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
            model.addAttribute("checkout", dto);
        }
        populateCheckout(model, cart);
        return "customer/order/checkout";
    }

    @PostMapping("/checkout")
    public String checkout(Authentication authentication,
                           @Valid @ModelAttribute("checkout") CheckoutDTO dto,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        User buyer = buyer(authentication);
        Cart cart = cartService.getOrCreate(buyer);
        if (bindingResult.hasErrors()) {
            populateCheckout(model, cart);
            return "customer/order/checkout";
        }

        try {
            List<CustomerOrder> orders = orderService.createOrders(buyer, dto);
            if (orders.size() > 1) {
                String message = "Placed " + orders.size() + " separate orders, one for each food cart.";
                if (dto.getPaymentMethod() == PaymentMethod.SSLCOMMERZ) {
                    message += " Open each order below to complete its demo payment.";
                }
                redirectAttributes.addFlashAttribute("success", message);
                return "redirect:/orders";
            }
            CustomerOrder order = orders.getFirst();
            if (dto.getPaymentMethod() == PaymentMethod.SSLCOMMERZ) {
                return "redirect:/payment/" + order.getOrderNumber();
            }
            redirectAttributes.addFlashAttribute("success", "Order placed successfully.");
            return "redirect:/orders/" + order.getOrderNumber();
        } catch (DiscountService.InvalidDiscountException exception) {
            bindingResult.rejectValue("discountCode", "invalid", exception.getMessage());
            populateCheckout(model, cartService.getOrCreate(buyer));
            return "customer/order/checkout";
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("checkout", exception.getMessage());
            populateCheckout(model, cartService.getOrCreate(buyer));
            return "customer/order/checkout";
        }
    }

    /**
     * Read-only preview of the EXACT server-side promotion calculation.
     * Checkout repeats validation at order creation, preventing client-side price tampering.
     */
    @GetMapping("/checkout/discount-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> discountPreview(Authentication authentication,
                                                                @RequestParam(defaultValue = "") String code) {
        User buyer = buyer(authentication);
        Cart cart = cartService.getOrCreate(buyer);
        BigDecimal subtotal = cartService.subtotal(cart);
        if (cart.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false, "message", "Add food to your cart before applying a promo code."));
        }
        try {
            DiscountService.AppliedDiscount applied = discountService.calculate(code, subtotal);
            BigDecimal total = subtotal.add(cartService.deliveryTotal(cart)).subtract(applied.amount());
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "discount", applied.amount().toPlainString(),
                    "total", total.toPlainString(),
                    "message", applied.discount() == null ? "No promo code applied." :
                            applied.discount().getName() + " applied."));
        } catch (DiscountService.InvalidDiscountException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false, "message", exception.getMessage()));
        }
    }

    @GetMapping("/orders")
    public String orders(Authentication authentication, Model model) {
        model.addAttribute("orders", orderService.buyerOrders(buyer(authentication)));
        return "customer/order/orders";
    }

    @GetMapping("/orders/{orderNumber}")
    public String order(Authentication authentication, @PathVariable String orderNumber, Model model) {
        CustomerOrder order = orderService.buyerOrder(buyer(authentication), orderNumber);
        model.addAttribute("order", order);
        model.addAttribute("payment", orderService.paymentFor(order));
        model.addAttribute("delivery", orderService.deliveryFor(order));
        model.addAttribute("canCancel", orderService.canBuyerCancel(order));
        model.addAttribute("progressStep", orderService.progressStep(order.getStatus()));
        return "customer/order/order-detail";
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public String cancel(Authentication authentication,
                         @PathVariable String orderNumber,
                         RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelByBuyer(buyer(authentication), orderNumber);
            redirectAttributes.addFlashAttribute("success", "Order cancelled.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/orders/" + orderNumber;
    }

    @GetMapping("/payment/{orderNumber}")
    public String payment(Authentication authentication, @PathVariable String orderNumber, Model model) {
        User buyer = buyer(authentication);
        CustomerOrder order = orderService.buyerOrder(buyer, orderNumber);
        Payment payment = orderService.paymentFor(order);
        model.addAttribute("order", order);
        model.addAttribute("payment", payment);
        model.addAttribute("demoMode", true);
        return "customer/order/payment";
    }

    @PostMapping("/payment/{orderNumber}/demo-complete")
    public String demoComplete(Authentication authentication,
                               @PathVariable String orderNumber,
                               RedirectAttributes redirectAttributes) {
        try {
            paymentService.completeDemoPayment(buyer(authentication), orderNumber);
            redirectAttributes.addFlashAttribute("success", "Demo payment completed successfully.");
        } catch (IllegalArgumentException | SecurityException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/orders/" + orderNumber;
    }

    private void populateCheckout(Model model, Cart cart) {
        model.addAttribute("cart", cart);
        model.addAttribute("subtotal", cartService.subtotal(cart));
        model.addAttribute("vendorGroups", cartService.vendorGroups(cart));
        model.addAttribute("deliveryTotal", cartService.deliveryTotal(cart));
        // Provide server-calculated values for form redisplays; browser preview is advisory only.
        DiscountService.AppliedDiscount applied = new DiscountService.AppliedDiscount(null, BigDecimal.ZERO);
        Object candidate = model.getAttribute("checkout");
        if (candidate instanceof CheckoutDTO dto && dto.getDiscountCode() != null
                && !dto.getDiscountCode().isBlank()) {
            try {
                applied = discountService.calculate(dto.getDiscountCode(), cartService.subtotal(cart));
            } catch (DiscountService.InvalidDiscountException ignored) {
                // Display validation error beside the code. Final amount stays undiscounted.
            }
        }
        model.addAttribute("promoDiscount", applied.amount());
        model.addAttribute("checkoutTotal", cartService.subtotal(cart)
                .add(cartService.deliveryTotal(cart)).subtract(applied.amount()));
        model.addAttribute("offers", discountService.activeDiscounts());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentMode", paymentService.paymentModeLabel());
    }

    private User buyer(Authentication authentication) {
        return userService.getBuyerByEmail(authentication.getName());
    }
}
