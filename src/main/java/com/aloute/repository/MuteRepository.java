package com.aloute.repository;

import com.aloute.entity.Mute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MuteRepository extends JpaRepository<Mute, Long> {

    boolean existsByMuterIdAndMutedId(Long muterId, Long mutedId);

    Optional<Mute> findByMuterIdAndMutedId(Long muterId, Long mutedId);

    org.springframework.data.domain.Page<Mute> findByMuterId(Long muterId, org.springframework.data.domain.Pageable pageable);

    void deleteByMuterIdAndMutedId(Long muterId, Long mutedId);
}
