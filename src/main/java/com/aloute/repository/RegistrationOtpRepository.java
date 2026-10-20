package com.aloute.repository;

import com.aloute.entity.RegistrationOtp;
import com.aloute.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {
    Optional<RegistrationOtp> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM RegistrationOtp o WHERE o.user.id = :userId")
    Optional<RegistrationOtp> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM RegistrationOtp o JOIN FETCH o.user u WHERE lower(u.email) = lower(:email)")
    Optional<RegistrationOtp> findByEmailForUpdate(@Param("email") String email);

    void deleteByUser(User user);
}
