package com.aloute.repository;

import com.aloute.entity.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {
    List<UserBackupCode> findByUserIdAndUsedFalse(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Long userId);

    long countByUserIdAndUsedFalse(Long userId);
}
