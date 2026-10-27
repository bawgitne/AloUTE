package com.aloute.repository;

import com.aloute.entity.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {
    List<UserBackupCode> findByUserIdAndUsedFalse(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Long userId);

    long countByUserIdAndUsedFalse(Long userId);

    /**
     * BL-08: atomic single-use redemption. The conditional UPDATE arbitrates
     * concurrent redemptions of the same code at the row level, so exactly one
     * caller observes a used=false → used=true transition.
     */
    @Modifying
    @Query("UPDATE UserBackupCode b SET b.used = true, b.usedAt = :now WHERE b.id = :id AND b.used = false")
    int redeemById(@Param("id") Long id, @Param("now") Instant now);
}
