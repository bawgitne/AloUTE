package com.aloute.controller.page;

import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.TopicService;
import com.aloute.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TopicPageController {

    private final TopicService topicService;
    private final PostService postService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"/topics/{identifier}", "/topic/{identifier}", "/t/{identifier}"})
    public String showTopicDetail(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception e) {
                log.warn("Could not retrieve current user profile for id {}", currentUserId);
            }
        }

        TopicResponse topic = null;
        boolean postsError = false;

        try {
            if (identifier.matches("\\d+")) {
                topic = topicService.get(Long.parseLong(identifier));
                if (topic != null && topic.slug() != null && !topic.slug().isBlank()) {
                    String query = (page > 0 || size != 20) ? "?page=" + page + "&size=" + size : "";
                    return "redirect:/topics/" + topic.slug() + query;
                }
            } else {
                topic = topicService.getByName(identifier);
            }
        } catch (Exception e) {
            log.warn("Topic not found for identifier {}: {}", identifier, e.getMessage());
        }

        List<PostResponse> posts = List.of();
        if (topic != null) {
            try {
                var postsRes = postService.topicPosts(topic.id(), Math.max(0, page), Math.max(1, Math.min(50, size)), currentUserId);
                if (postsRes != null && postsRes.getData() != null) {
                    posts = postsRes.getData();
                }
            } catch (Exception e) {
                log.warn("Error loading posts for topic id {}: {}", topic.id(), e.getMessage());
                postsError = true;
            }
        }

        model.addAttribute("topic", topic);
        model.addAttribute("postsError", postsError);
        model.addAttribute("posts", posts);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "topics");
        model.addAttribute("activeTab", "topics");

        return "topic/detail";
    }
}
