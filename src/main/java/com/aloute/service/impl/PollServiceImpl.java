package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.VotePollRequest;
import com.aloute.dto.response.PollOptionResponse;
import com.aloute.dto.response.PollResponse;
import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.PollVote;
import com.aloute.entity.User;
import com.aloute.repository.PollOptionRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PollVoteRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.PollService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class PollServiceImpl implements PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PollResponse getPollById(Long pollId, Long currentUserId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND", "Poll not found"));
        return mapToPollResponse(poll, currentUserId);
    }

    @Override
    @Transactional
    public PollResponse votePoll(Long pollId, Long currentUserId, VotePollRequest request) {
        // Lock the poll row for the whole vote: the single-choice one-vote rule is a
        // check-then-insert sequence, so concurrent same-user votes must be serialized
        // for the exists() check to be trustworthy (UNIQUE(poll_id, option_id, user_id)
        // only blocks identical options, not different options from the same user).
        Poll poll = pollRepository.findByIdForUpdate(pollId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND", "Poll not found"));

        if (poll.isClosed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_CLOSED", "This poll is closed");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        List<Long> optionIds = request.getEffectiveOptionIds().stream().distinct().toList();
        if (optionIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "At least one option ID is required to vote");
        }

        // Single-choice invariant (domain §17.1): enforced in the application layer;
        // the DB unique constraint is deliberately (poll_id, option_id, user_id) only.
        if (!poll.isAllowMultiple()) {
            if (optionIds.size() > 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_NOT_MULTIPLE_CHOICE", "This poll only allows voting for one option");
            }
            if (pollVoteRepository.existsByPollIdAndUserId(pollId, currentUserId)) {
                throw new ApiException(HttpStatus.CONFLICT, "POLL_ALREADY_VOTED", "You have already voted in this poll");
            }
        }

        List<PollVote> newVotes = new java.util.ArrayList<>();
        List<Long> newlyVotedOptionIds = new java.util.ArrayList<>();
        for (Long optionId : optionIds) {
            PollOption option = pollOptionRepository.findById(optionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POLL_OPTION_NOT_FOUND", "Poll option not found"));

            if (!option.getPoll().getId().equals(pollId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Option does not belong to this poll");
            }

            if (!pollVoteRepository.existsByPollIdAndOptionIdAndUserId(pollId, optionId, currentUserId)) {
                newVotes.add(PollVote.builder()
                        .poll(poll)
                        .option(option)
                        .user(user)
                        .build());
                newlyVotedOptionIds.add(optionId);
            }
        }

        // Two concurrent transactions can both pass the exists() checks above for the same
        // (poll, option, user); the DB unique constraint uk_poll_votes_user_option is the
        // final arbiter. Force the flush inside the try so the violation surfaces here and
        // maps to a friendly 409 instead of a 500 at commit time.
        try {
            pollVoteRepository.saveAll(newVotes);
            pollVoteRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "POLL_ALREADY_VOTED", "You have already voted in this poll");
        }

        // Cached vote counts are advanced atomically (bulk UPDATE), never read-modify-write.
        for (Long optionId : newlyVotedOptionIds) {
            pollOptionRepository.incrementVoteCount(optionId);
        }

        log.info("User {} voted in poll {}", currentUserId, pollId);
        return mapToPollResponse(poll, currentUserId);
    }

    @Override
    public PollResponse mapToPollResponse(Poll poll, Long currentUserId) {
        // One query for all of the viewer's votes in this poll; the previous
        // per-option exists() probe was an option-count N+1.
        Set<Long> selectedOptionIds = currentUserId != null
                ? pollVoteRepository.findByPollIdAndUserId(poll.getId(), currentUserId).stream()
                        .map(vote -> vote.getOption().getId())
                        .collect(Collectors.toSet())
                : Set.of();

        List<PollOptionResponse> options = pollOptionRepository.findByPollIdOrderByPositionAsc(poll.getId()).stream()
                .map(opt -> new PollOptionResponse(opt.getId(), opt.getOptionText(), opt.getVoteCount(),
                        currentUserId == null ? null : selectedOptionIds.contains(opt.getId())))
                .toList();

        int totalVotes = options.stream().mapToInt(PollOptionResponse::getVoteCount).sum();
        Long userVotedOptionId = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getSelectedByCurrentUser()))
                .map(PollOptionResponse::getId)
                .findFirst()
                .orElse(null);

        return new PollResponse(
                poll.getId(),
                poll.getQuestion(),
                poll.isAllowMultiple(),
                poll.getClosedAt(),
                poll.getClosedAt(),
                totalVotes,
                userVotedOptionId,
                options
        );
    }
}
