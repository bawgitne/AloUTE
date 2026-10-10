package com.aloute.controller.page;

import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.ConversationService;
import com.aloute.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessagingPageController {

    private final ConversationService conversationService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/messages")
    public String showMessaging(
            @PageableDefault(size = 20) Pageable pageable,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        ProfileResponse currentUser = null;
        try {
            currentUser = userService.getProfile(currentUserId);
        } catch (Exception e) {
            log.warn("Could not retrieve current user profile for id {}", currentUserId);
        }

        Page<ConversationResponse> conversations = conversationService.getConversations(currentUserId, pageable);

        model.addAttribute("conversations", conversations.getContent());
        model.addAttribute("page", conversations);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "messages");

        return "messaging/index";
    }

    @GetMapping("/messages/{conversationId}")
    public String showConversation(
            @PathVariable Long conversationId,
            @PageableDefault(size = 30) Pageable pageable,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        ProfileResponse currentUser = null;
        try {
            currentUser = userService.getProfile(currentUserId);
        } catch (Exception e) {
            log.warn("Could not retrieve current user profile for id {}", currentUserId);
        }

        try {
            ConversationResponse currentConversation = conversationService.getConversation(conversationId, currentUserId);
            model.addAttribute("currentConversation", currentConversation);
        } catch (Exception e) {
            log.warn("Could not retrieve conversation id {}", conversationId);
        }

        Page<ConversationResponse> conversations = conversationService.getConversations(currentUserId, pageable);

        model.addAttribute("conversations", conversations.getContent());
        model.addAttribute("page", conversations);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "messages");

        return "messaging/index";
    }
}
