package com.aloute.repository;

import com.aloute.entity.Post;
import com.aloute.entity.PostVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query(value = "select * from sp_get_visible_post(:postId, :viewerId)", nativeQuery = true)
    Optional<Post> getVisiblePostFromSp(@Param("postId") Long postId, @Param("viewerId") Long viewerId);

    @Query(value = "select * from sp_list_public_posts(:pOffset, :pLimit)", nativeQuery = true)
    List<Post> listPublicPostsFromSp(@Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_public_posts()", nativeQuery = true)
    long countPublicPostsFromSp();

    @Query(value = "select * from sp_list_post_replies(:postId, :pOffset, :pLimit)", nativeQuery = true)
    List<Post> listRepliesFromSp(@Param("postId") Long postId, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_post_replies(:postId)", nativeQuery = true)
    long countRepliesFromSp(@Param("postId") Long postId);

    @Query(value = "select * from sp_list_community_posts(:communityId, :pOffset, :pLimit)", nativeQuery = true)
    List<Post> listCommunityPostsFromSp(@Param("communityId") Long communityId, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_community_posts(:communityId)", nativeQuery = true)
    long countCommunityPostsFromSp(@Param("communityId") Long communityId);

    @Query(value = "select * from sp_list_topic_posts(:topicId, :pOffset, :pLimit)", nativeQuery = true)
    List<Post> listTopicPostsFromSp(@Param("topicId") Long topicId, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_topic_posts(:topicId)", nativeQuery = true)
    long countTopicPostsFromSp(@Param("topicId") Long topicId);

    @Query(value = "select sp_has_liked_post(:postId, :userId)", nativeQuery = true)
    boolean hasLikedFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_has_bookmarked_post(:postId, :userId)", nativeQuery = true)
    boolean hasBookmarkedFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_has_reposted_post(:postId, :userId)", nativeQuery = true)
    boolean hasRepostedFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_create_post(:userId, :content, :visibility, :allowReply, :allowQuote, :parentPostId, :quotedPostId, :communityId, :mediaIds, :topicIds)", nativeQuery = true)
    Long createPostFromSp(@Param("userId") Long userId,
                          @Param("content") String content,
                          @Param("visibility") String visibility,
                          @Param("allowReply") boolean allowReply,
                          @Param("allowQuote") boolean allowQuote,
                          @Param("parentPostId") Long parentPostId,
                          @Param("quotedPostId") Long quotedPostId,
                          @Param("communityId") Long communityId,
                          @Param("mediaIds") String mediaIds,
                          @Param("topicIds") String topicIds);

    @Query(value = "select sp_update_post(:postId, :userId, :content, :visibility, :allowReply, :allowQuote)", nativeQuery = true)
    Long updatePostFromSp(@Param("postId") Long postId,
                          @Param("userId") Long userId,
                          @Param("content") String content,
                          @Param("visibility") String visibility,
                          @Param("allowReply") Boolean allowReply,
                          @Param("allowQuote") Boolean allowQuote);

    @Query(value = "select sp_soft_delete_post_cascade(:postId, :userId)", nativeQuery = true)
    Long softDeletePostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_like_post(:postId, :userId)", nativeQuery = true)
    Long likePostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_unlike_post(:postId, :userId)", nativeQuery = true)
    Long unlikePostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_bookmark_post(:postId, :userId)", nativeQuery = true)
    Long bookmarkPostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_remove_bookmark(:postId, :userId)", nativeQuery = true)
    Long removeBookmarkFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_repost_post(:postId, :userId)", nativeQuery = true)
    Long repostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query(value = "select sp_remove_repost(:postId, :userId)", nativeQuery = true)
    Long removeRepostFromSp(@Param("postId") Long postId, @Param("userId") Long userId);

    default String visibilityValue(PostVisibility visibility) {
        return visibility == null ? null : visibility.name();
    }
}
