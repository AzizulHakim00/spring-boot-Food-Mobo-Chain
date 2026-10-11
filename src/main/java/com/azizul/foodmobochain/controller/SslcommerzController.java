package com.azizul.foodmobochain.controller;

import com.azizul.foodmobochain.service.PaymentService;
import com.azizul.foodmobochain.service.SslcommerzGatewayService;
import com.azizul.foodmobochain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.net.URI;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SslcommerzController {
    private final PaymentService payments;
    private final UserService users;

    @PostMapping("/payment/{orderNumber}/sandbox-start")
    public String start(Authentication auth, @PathVariable String orderNumber,
                        RedirectAttributes flash) {
        try {
            var session = payments.startSandbox(users.getBuyerByEmail(auth.getName()), orderNumber);
            return "redirect:" + session.gatewayUrl();
        } catch (IllegalArgumentException | SecurityException | IllegalStateException ex) {
            flash.addFlashAttribute("error", "Unable to start sandbox checkout. Please try again.");
            return "redirect:/payment/" + orderNumber;
        }
    }

    // Browser return is advisory; only the server-to-server validation API confirms payment.
    @GetMapping("/payment/ssl/success")
    public String successGet(@RequestParam Map<String, String> fields) {
        return finishBrowserSuccess(fields);
    }

    @PostMapping("/payment/ssl/success")
    public String successPost(@RequestParam Map<String, String> fields) {
        return finishBrowserSuccess(fields);
    }

    private String finishBrowserSuccess(Map<String, String> fields) {
        String validationId = fields.get("val_id");
        if (validationId == null || validationId.isBlank()) {
            return "redirect:/orders?payment=unverified";
        }
        try {
            payments.confirmSandbox(validationId);
            return "redirect:/orders?payment=verified";
        } catch (RuntimeException exception) {
            return "redirect:/orders?payment=unverified";
        }
    }

    @PostMapping("/payment/ssl/ipn")
    @ResponseBody
    public ResponseEntity<String> ipn(@RequestParam Map<String, String> fields) {
        String validationId = fields.get("val_id");
        if (validationId == null || validationId.isBlank()) return ResponseEntity.badRequest().body("Missing val_id");
        try {
            payments.confirmSandbox(validationId);
            return ResponseEntity.ok("OK");
        } catch (SecurityException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body("Rejected");
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Retry later");
        }
    }

    @GetMapping({"/payment/ssl/fail", "/payment/ssl/cancel"})
    public String browserFailureGet() { return "redirect:/orders?payment=incomplete"; }

    @PostMapping({"/payment/ssl/fail", "/payment/ssl/cancel"})
    public String browserFailurePost() { return "redirect:/orders?payment=incomplete"; }
}
