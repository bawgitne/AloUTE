package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.VotePollRequest;
import com.aloute.dto.response.PollResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PollService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{pollId}")
    public ApiResponse<PollResponse> getPollById(@PathVariable Long pollId) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        PollResponse response = pollService.getPollById(pollId, currentUserId);
        return ApiResponse.success(response);
    }

    @PostMapping({"/{pollId}/votes", "/{pollId}/vote"})
    public ApiResponse<PollResponse> votePoll(
            @PathVariable Long pollId,
            @Valid @RequestBody VotePollRequest request
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        PollResponse response = pollService.votePoll(pollId, currentUserId, request);
        return ApiResponse.success(response);
    }
}
