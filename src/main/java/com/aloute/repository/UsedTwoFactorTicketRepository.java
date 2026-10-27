package com.aloute.repository;

import com.aloute.entity.UsedTwoFactorTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UsedTwoFactorTicketRepository extends JpaRepository<UsedTwoFactorTicket, Long> {

    boolean existsByTicketHash(String ticketHash);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByConsumedAtBefore(Instant cutoff);
}
