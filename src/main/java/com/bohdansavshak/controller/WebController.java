package com.bohdansavshak.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @GetMapping("/")
    public String index() {
        logger.info("Serving chat page");
        return "chat";
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        logger.info("Serving chat page");
        model.addAttribute("pageTitle", "Sombra Confluence Chat");
        return "chat";
    }
}