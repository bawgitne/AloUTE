package com.aloute.repository;

import com.aloute.entity.Poll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {
    Optional<Poll> findByPostId(Long postId);
    List<Poll> findByPost_IdIn(Collection<Long> postIds);

    /**
     * Serializes voting on a poll. The single-choice one-vote check is a
     * check-then-insert sequence; without this row lock two concurrent
     * transactions from the same user (different options) can both pass the
     * exists() check, because UNIQUE(poll_id, option_id, user_id) only blocks
     * identical options. SELECT ... FOR UPDATE works on MySQL 8 (InnoDB) and H2.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Poll p WHERE p.id = :id")
    Optional<Poll> findByIdForUpdate(@Param("id") Long id);
}
