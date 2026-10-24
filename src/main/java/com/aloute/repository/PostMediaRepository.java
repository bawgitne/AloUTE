package com.aloute.repository;

import com.aloute.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {
    List<PostMedia> findByPost_IdOrderByPositionAsc(Long postId);
    List<PostMedia> findByPost_IdInOrderByPost_IdAscPositionAsc(List<Long> postIds);
}
