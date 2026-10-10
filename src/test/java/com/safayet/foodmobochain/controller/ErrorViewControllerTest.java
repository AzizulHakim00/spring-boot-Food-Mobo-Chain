package com.safayet.foodmobochain.controller;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.*;

class ErrorViewControllerTest {
    private final ErrorViewController page = new ErrorViewController();

    @Test
    void expiredCheckoutFormShowsAccurateReasonAndSafeRetry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error/403");
        request.setAttribute(WebAttributes.ACCESS_DENIED_403, new MissingCsrfTokenException(null));
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/checkout");
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("error/403", page.forbidden(request, model));
        assertEquals(true, model.get("csrfFailure"));
        assertEquals("/checkout", model.get("retryUrl"));
    }

    @Test
    void paymentCsrfFailureRecoversToPaymentGetNotUnsafePost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error/403");
        request.setAttribute(WebAttributes.ACCESS_DENIED_403, new MissingCsrfTokenException(null));
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI,
                "/payment/FMC-20261011-A9B1/demo-complete");
        ExtendedModelMap model = new ExtendedModelMap();

        page.forbidden(request, model);
        assertEquals(true, model.get("csrfFailure"));
        assertEquals("/payment/FMC-20261011-A9B1", model.get("retryUrl"));
    }

    @Test
    void actualPermissionDenialIsNotDescribedAsCsrf() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error/403");
        ExtendedModelMap model = new ExtendedModelMap();

        page.forbidden(request, model);
        assertEquals(false, model.get("csrfFailure"));
        assertNull(model.get("retryUrl"));
    }

    @Test
    void maliciousForwardedPathCannotBecomeRecoveryLink() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error/403");
        request.setAttribute(WebAttributes.ACCESS_DENIED_403, new MissingCsrfTokenException(null));
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "//other.example/pay");
        ExtendedModelMap model = new ExtendedModelMap();

        page.forbidden(request, model);
        assertEquals("/", model.get("retryUrl"));
    }
}
