package com.safayet.foodmobochain.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorViewController {

    @RequestMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }
}
