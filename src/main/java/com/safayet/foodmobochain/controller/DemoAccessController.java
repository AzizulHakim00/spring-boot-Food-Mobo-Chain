package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.FoodItem;
import com.safayet.foodmobochain.security.DemoSessionService;
import com.safayet.foodmobochain.security.DemoSessionService.DemoScope;
import com.safayet.foodmobochain.service.CatalogService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only demonstration using public catalog fields only. Demo cookies do not
 * authenticate /admin, /seller, /orders, /api or any other protected route.
 */
@Controller
@RequiredArgsConstructor
public class DemoAccessController {
    private final DemoSessionService sessions;
    private final CatalogService catalog;
    @org.springframework.beans.factory.annotation.Value("${app.demo.full-url:}")
    private String fullDemoUrl;

    @PostMapping("/demo/start")
    public String start(@RequestParam String role,
                        @RequestParam(name = "store", required = false) String store,
                        HttpServletResponse response) {
        requireStagingDemo();
        try {
            DemoScope scope = sessions.choose(role, store);
            if ("https://food-mobo-chain-demo.onrender.com".equals(fullDemoUrl)) {
                // This hostname uses only the independent, synthetic demonstration database.
                String destination = fullDemoUrl + "/demo/enter?role=" + scope.role()
                        + ("seller".equals(scope.role()) ? "&store=" + scope.storeSlug() : "");
                return "redirect:" + destination;
            }
            sessions.issueCookie(response, scope);
            return "redirect:/demo";
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demo selection");
        }
    }

    @GetMapping("/demo")
    public String dashboard(
            @CookieValue(name = DemoSessionService.COOKIE_NAME, required = false) String token,
            Model model) {
        requireStagingDemo();
        final DemoScope scope;
        try {
            scope = sessions.verify(token);
        } catch (JwtException | IllegalArgumentException invalid) {
            return "redirect:/login?demoExpired";
        }

        List<FoodCart> publicCarts = catalog.carts();
        model.addAttribute("role", scope.role());
        model.addAttribute("cartCount", publicCarts.size());
        model.addAttribute("categoryCount", catalog.categories().size());
        model.addAttribute("publicCarts", publicCarts.stream().limit(8).toList());

        if ("seller".equals(scope.role())) {
            FoodCart store = publicCarts.stream()
                    .filter(cart -> scope.storeSlug().equals(cart.getSlug()))
                    .findFirst().orElse(null);
            model.addAttribute("selectedStore", store);
            model.addAttribute("storeName", DemoSessionService.storeName(scope.storeSlug()));
            List<FoodItem> items = store == null
                    ? List.of() : catalog.menu(store).stream().limit(9).toList();
            model.addAttribute("previewFoods", items);
            model.addAttribute("foodCount", items.size());
        } else {
            var sample = catalog.searchFoods(null, null, null, null, null, "popular", 0, 9);
            model.addAttribute("previewFoods", sample.getContent());
            model.addAttribute("foodCount", sample.getTotalElements());
        }
        return "demo/dashboard";
    }

    @PostMapping("/demo/exit")
    public String exit(HttpServletResponse response) {
        sessions.clearCookie(response);
        return "redirect:/login";
    }

    private void requireStagingDemo() {
        if (!sessions.isEnabled())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
