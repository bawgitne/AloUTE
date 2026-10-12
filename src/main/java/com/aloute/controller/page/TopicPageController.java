package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.TopicService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class TopicPageController {

    private final TopicService topicService;
    private final PostService postService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"/topic/{name}", "/t/{name}"})
    public String showTopicDetail(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        TopicResponse topic = null;
        try {
            topic = topicService.getByName(name);
        } catch (Exception e) {
            log.warn("Topic not found: {}", name);
        }

        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception ignored) {}
        }

        var postsRes = (topic != null)
                ? postService.topicPosts(topic.id(), page, size, currentUserId)
                : null;

        model.addAttribute("topic", topic);
        model.addAttribute("posts", postsRes != null ? postsRes.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "feed");
        model.addAttribute("activeTab", "feed");

        return "topic/detail";
    }
}
