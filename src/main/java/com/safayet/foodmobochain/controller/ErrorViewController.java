package com.safayet.foodmobochain.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorViewController {

    @GetMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }
}
