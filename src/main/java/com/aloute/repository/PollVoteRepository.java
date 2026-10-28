package com.aloute.repository;

import com.aloute.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    boolean existsByPollIdAndUserId(Long pollId, Long userId);
    boolean existsByPollIdAndOptionIdAndUserId(Long pollId, Long optionId, Long userId);
    Optional<PollVote> findByPollIdAndOptionIdAndUserId(Long pollId, Long optionId, Long userId);
    List<PollVote> findByPollId(Long pollId);
    List<PollVote> findByPollIdAndUserId(Long pollId, Long userId);

    /** Option ids the user already voted for in this poll, fetched without lazy-loading options. */
    @Query("SELECT v.option.id FROM PollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    List<Long> findOptionIdsByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);
    List<PollVote> findByPoll_IdInAndUser_Id(Collection<Long> pollIds, Long userId);
    long countByOptionId(Long optionId);
    long countByPollId(Long pollId);
}
