package com.aloute.repository;

import com.aloute.entity.Restriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestrictionRepository extends JpaRepository<Restriction, Long> {

    boolean existsByRestricterIdAndRestrictedId(Long restricterId, Long restrictedId);

    Optional<Restriction> findByRestricterIdAndRestrictedId(Long restricterId, Long restrictedId);

    void deleteByRestricterIdAndRestrictedId(Long restricterId, Long restrictedId);
}
