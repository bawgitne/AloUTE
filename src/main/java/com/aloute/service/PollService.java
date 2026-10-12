package com.aloute.service;

import com.aloute.dto.request.VotePollRequest;
import com.aloute.dto.response.PollResponse;
import com.aloute.entity.Poll;

public interface PollService {
    PollResponse getPollById(Long pollId, Long currentUserId);
    PollResponse votePoll(Long pollId, Long currentUserId, VotePollRequest request);
    PollResponse mapToPollResponse(Poll poll, Long currentUserId);
}
