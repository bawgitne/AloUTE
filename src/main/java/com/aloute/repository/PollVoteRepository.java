package com.aloute.repository;

import com.aloute.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    boolean existsByPollIdAndUserId(Long pollId, Long userId);
    boolean existsByPollIdAndOptionIdAndUserId(Long pollId, Long optionId, Long userId);
    Optional<PollVote> findByPollIdAndOptionIdAndUserId(Long pollId, Long optionId, Long userId);
    List<PollVote> findByPollIdAndUserId(Long pollId, Long userId);
    long countByOptionId(Long optionId);
    long countByPollId(Long pollId);
}
