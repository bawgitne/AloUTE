package com.aloute.repository;

import com.aloute.entity.PasswordResetToken;
import com.aloute.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    Optional<PasswordResetToken> findByUser(User user);
    void deleteByUser(User user);

    @org.springframework.data.jpa.repository.Query("SELECT t.user.id FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@org.springframework.data.repository.query.Param("tokenHash") String tokenHash);
}
