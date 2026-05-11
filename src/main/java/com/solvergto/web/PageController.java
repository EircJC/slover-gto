package com.solvergto.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/trainer-demo")
    public String trainerDemo() {
        return "forward:/trainer-demo.html";
    }

    @GetMapping("/trainer")
    public String trainer() {
        return "forward:/trainer-demo.html";
    }

    @GetMapping("/trainer/history")
    public String trainerHistory() {
        return "forward:/trainer-history.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/auth.html";
    }
}
