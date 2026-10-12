package com.aloute.controller.page;

import lombok.RequiredArgsConstructor;
import com.aloute.security.currentuser.CurrentUserProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Controller
public class AuthPageController {
    private final Environment environment;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/")
    public String rootPage() {
        if (currentUserProvider.getCurrentUserId().isPresent()) {
            return "redirect:/feed";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        if (currentUserProvider.getCurrentUserId().isPresent()) {
            return "redirect:/feed";
        }
        model.addAttribute("devTestAccounts", environment.acceptsProfiles(Profiles.of("dev")));
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        if (currentUserProvider.getCurrentUserId().isPresent()) {
            return "redirect:/feed";
        }
        return "auth/register";
    }
}
