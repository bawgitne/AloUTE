package com.aloute.repository;

import com.aloute.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    @Query("select coalesce(sum(m.fileSize), 0) from MediaAsset m where m.owner.id = :ownerId and m.deletedAt is null")
    long sumActiveBytesByOwner(@Param("ownerId") Long ownerId);

    List<MediaAsset> findByIdInOrderByIdAsc(List<Long> ids);

    Optional<MediaAsset> findByIdAndOwner_IdAndDeletedAtIsNull(Long id, Long ownerId);
}
