package com.aloute.controller.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dev-only test page for Khoi's backend APIs.
 * Kept outside controller/page because Thymeleaf application pages are owned by Quan.
 */
@Controller
@Profile("dev")
public class KhoiBackendTestController {

    @GetMapping({"/khoi-test", "/khoi-test/"})
    public String backendTestPage() {
        return "khoi-test/index";
    }
}
