package com.aloute.service;

import com.aloute.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class RevokedTokenCleanupService {

    private final RevokedTokenRepository revokedTokenRepository;

    @Scheduled(cron = "${aloute.auth.revoked-token-cleanup-cron:0 0 * * * *}")
    @Transactional
    public int cleanupExpiredTokens() {
        Instant now = Instant.now();
        int deleted = revokedTokenRepository.deleteExpiredTokens(now);
        if (deleted > 0) {
            log.info("Expired revoked tokens cleanup completed: {} entries removed", deleted);
        }
        return deleted;
    }
}
