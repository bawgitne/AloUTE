package com.aloute.repository;

import com.aloute.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, Long> {

    List<UserFcmToken> findByUserId(Long userId);

    Optional<UserFcmToken> findByToken(String token);

    void deleteByUserId(Long userId);

    void deleteByToken(String token);
}
