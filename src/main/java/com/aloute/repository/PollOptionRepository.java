package com.aloute.repository;

import com.aloute.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPollIdOrderByPositionAsc(Long pollId);

    /** Batch lookup for list assembly: options of every poll on a page, in one query. */
    List<PollOption> findByPoll_IdInOrderByPoll_IdAscPositionAsc(Collection<Long> pollIds);

    /**
     * Atomic vote-count increment. A bulk UPDATE avoids the read-modify-write lost-update
     * race when concurrent transactions vote on the same option. clearAutomatically evicts
     * the stale first-level-cache entities so subsequent reads observe the new count.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId")
    int incrementVoteCount(@Param("optionId") Long optionId);
}
