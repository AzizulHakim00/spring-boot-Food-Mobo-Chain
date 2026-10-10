package com.safayet.foodmobochain.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorViewController {

    @RequestMapping("/error/403")
    public String forbidden(HttpServletRequest request, Model model) {
        Object reason = request.getAttribute(WebAttributes.ACCESS_DENIED_403);
        boolean csrfFailure = reason instanceof CsrfException;
        model.addAttribute("csrfFailure", csrfFailure);
        if (csrfFailure) {
            model.addAttribute("retryUrl", safeRetryUrl(request));
        }
        return "error/403";
    }

    /** Never redirect to an untrusted URL; allow only known local seller/buyer forms. */
    private static String safeRetryUrl(HttpServletRequest request) {
        Object forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (!(forwarded instanceof String path)) return "/";
        if (path.equals("/checkout") || path.startsWith("/checkout/")) return "/checkout";
        if (path.startsWith("/cart/") || path.equals("/cart")) return "/cart";
        if (path.matches("/payment/[A-Za-z0-9_-]{3,100}/demo-complete")) {
            return path.substring(0, path.length() - "/demo-complete".length());
        }
        if (path.startsWith("/seller/")) return "/seller";
        return "/";
    }
}
