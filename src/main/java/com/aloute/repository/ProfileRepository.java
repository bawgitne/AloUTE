package com.aloute.repository;

import com.aloute.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /**
     * Batch lookup for list assembly. The user is fetch-joined so building
     * UserSummary rows does not trigger one lazy init per author.
     */
    @Query("""
            SELECT p FROM Profile p
            JOIN FETCH p.user
            WHERE p.userId IN :userIds
            """)
    List<Profile> findWithUserByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
