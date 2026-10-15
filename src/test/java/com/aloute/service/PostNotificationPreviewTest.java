package com.aloute.service;

import com.aloute.dto.request.CreatePostRequest;
import com.aloute.entity.*;
import com.aloute.event.NotificationEvent;
import com.aloute.repository.*;
import com.aloute.service.impl.PostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Focused tests for P0 notification preview protection on POST_REPLY and POST_QUOTE events.
 * Verifies that previews are omitted (null) when the notification recipient does not have
 * permission to read the reply or quote post, preventing private content leakage.
 */
class PostNotificationPreviewTest {

    private PostRepository postRepository;
    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private PostMediaRepository postMediaRepository;
    private PostTopicRepository postTopicRepository;
    private TopicRepository topicRepository;
    private CommunityMemberRepository communityMemberRepository;
    private CommunityRepository communityRepository;
    private PostLikeRepository postLikeRepository;
    private BookmarkRepository bookmarkRepository;
    private RepostRepository repostRepository;
    private MediaAssetRepository mediaAssetRepository;
    private BlockRepository blockRepository;
    private FollowRepository followRepository;
    private ApplicationEventPublisher eventPublisher;

    private PostServiceImpl postService;

    private User replier;
    private User parentAuthor;
    private User quotedAuthor;
    private Post parentPost;
    private Post quotedPost;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        postMediaRepository = mock(PostMediaRepository.class);
        postTopicRepository = mock(PostTopicRepository.class);
        topicRepository = mock(TopicRepository.class);
        communityMemberRepository = mock(CommunityMemberRepository.class);
        communityRepository = mock(CommunityRepository.class);
        postLikeRepository = mock(PostLikeRepository.class);
        bookmarkRepository = mock(BookmarkRepository.class);
        repostRepository = mock(RepostRepository.class);
        mediaAssetRepository = mock(MediaAssetRepository.class);
        blockRepository = mock(BlockRepository.class);
        followRepository = mock(FollowRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        postService = new PostServiceImpl(
                postRepository,
                userRepository,
                profileRepository,
                postMediaRepository,
                postTopicRepository,
                topicRepository,
                communityMemberRepository,
                communityRepository,
                postLikeRepository,
                bookmarkRepository,
                repostRepository,
                mediaAssetRepository,
                blockRepository,
                followRepository,
                eventPublisher
        );

        replier = new User();
        replier.setId(1L);
        replier.setUsername("replier");
        replier.setActive(true);
        replier.setPrivateAccount(false);

        parentAuthor = new User();
        parentAuthor.setId(2L);
        parentAuthor.setUsername("parentAuthor");
        parentAuthor.setActive(true);
        parentAuthor.setPrivateAccount(false);

        quotedAuthor = new User();
        quotedAuthor.setId(3L);
        quotedAuthor.setUsername("quotedAuthor");
        quotedAuthor.setActive(true);
        quotedAuthor.setPrivateAccount(false);

        parentPost = new Post();
        parentPost.setId(10L);
        parentPost.setUser(parentAuthor);
        parentPost.setContent("Parent post content");
        parentPost.setVisibility(PostVisibility.PUBLIC);
        parentPost.setAllowReply(true);
        parentPost.setAllowQuote(true);

        quotedPost = new Post();
        quotedPost.setId(20L);
        quotedPost.setUser(quotedAuthor);
        quotedPost.setContent("Quoted post content");
        quotedPost.setVisibility(PostVisibility.PUBLIC);
        quotedPost.setAllowReply(true);
        quotedPost.setAllowQuote(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(replier));
        when(userRepository.findById(2L)).thenReturn(Optional.of(parentAuthor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(quotedAuthor));

        when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(parentPost));
        when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(parentPost));
        when(postRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(quotedPost));
        when(postRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(quotedPost));

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(999L);
            }
            return p;
        });

        when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenAnswer(invocation -> {
            Post p = new Post();
            p.setId(999L);
            p.setUser(replier);
            p.setContent("Test reply or quote");
            p.setVisibility(PostVisibility.PUBLIC);
            p.setAllowReply(true);
            p.setAllowQuote(true);
            return Optional.of(p);
        });

        when(profileRepository.findById(anyLong())).thenReturn(Optional.of(new Profile()));
    }

    @Test
    @DisplayName("P0 Positive: Public reply notification preserves valid content preview")
    void publicReplyPreservesPreview() {
        CreatePostRequest request = new CreatePostRequest(
                "Great discussion point!",
                PostVisibility.PUBLIC,
                true,
                true,
                null,
                null,
                null,
                null
        );

        postService.create(1L, request, null, 10L, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.recipientId()).isEqualTo(2L);
        assertThat(event.actorId()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo("POST_REPLY");
        assertThat(event.targetType()).isEqualTo("POST");
        assertThat(event.targetId()).isEqualTo(999L);
        assertThat(event.contentPreview()).isEqualTo("Great discussion point!");
    }

    @Test
    @DisplayName("P0 Negative: Followers-only reply omits preview when parent author is not a follower")
    void followersOnlyReplyOmitsPreviewForNonFollower() {
        CreatePostRequest request = new CreatePostRequest(
                "Private reply for my followers only",
                PostVisibility.FOLLOWERS,
                true,
                true,
                null,
                null,
                null,
                null
        );

        // parentAuthor (2L) does NOT follow replier (1L)
        when(followRepository.existsById(new FollowId(2L, 1L))).thenReturn(false);

        postService.create(1L, request, null, 10L, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.recipientId()).isEqualTo(2L);
        assertThat(event.actorId()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo("POST_REPLY");
        assertThat(event.targetId()).isEqualTo(999L);
        assertThat(event.contentPreview()).isNull();
    }

    @Test
    @DisplayName("P0 Positive: Followers-only reply includes preview when parent author is a follower")
    void followersOnlyReplyPreservesPreviewForFollower() {
        CreatePostRequest request = new CreatePostRequest(
                "Private reply for my followers only",
                PostVisibility.FOLLOWERS,
                true,
                true,
                null,
                null,
                null,
                null
        );

        // parentAuthor (2L) DOES follow replier (1L)
        when(followRepository.existsById(new FollowId(2L, 1L))).thenReturn(true);

        postService.create(1L, request, null, 10L, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.contentPreview()).isEqualTo("Private reply for my followers only");
    }

    @Test
    @DisplayName("P0 Negative: Private community reply omits preview when parent author is not a community member")
    void privateCommunityReplyOmitsPreviewForNonMember() {
        Community privateComm = new Community();
        privateComm.setId(50L);
        privateComm.setPrivateCommunity(true);

        when(communityRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(privateComm));
        // replier is member
        when(communityMemberRepository.existsByCommunityIdAndUserId(50L, 1L)).thenReturn(true);
        // parent author is NOT member
        when(communityMemberRepository.existsByCommunityIdAndUserId(50L, 2L)).thenReturn(false);

        CreatePostRequest request = new CreatePostRequest(
                "Secret group reply content",
                PostVisibility.PUBLIC,
                true,
                true,
                null,
                null,
                null,
                null
        );

        postService.create(1L, request, 50L, 10L, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.contentPreview()).isNull();
    }

    @Test
    @DisplayName("P0 Negative: Private author reply omits preview for non-follower parent author")
    void privateAuthorReplyOmitsPreviewForNonFollower() {
        replier.setPrivateAccount(true);

        CreatePostRequest request = new CreatePostRequest(
                "Reply from private account",
                PostVisibility.PUBLIC,
                true,
                true,
                null,
                null,
                null,
                null
        );

        when(followRepository.existsById(new FollowId(2L, 1L))).thenReturn(false);

        postService.create(1L, request, null, 10L, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.contentPreview()).isNull();
    }

    @Test
    @DisplayName("P0 Positive: Public quote notification preserves valid content preview")
    void publicQuotePreservesPreview() {
        CreatePostRequest request = new CreatePostRequest(
                "Quoting your great post publicly",
                PostVisibility.PUBLIC,
                true,
                true,
                null,
                null,
                null,
                null
        );

        postService.create(1L, request, null, null, 20L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.recipientId()).isEqualTo(3L);
        assertThat(event.actorId()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo("POST_QUOTE");
        assertThat(event.targetId()).isEqualTo(999L);
        assertThat(event.contentPreview()).isEqualTo("Quoting your great post publicly");
    }

    @Test
    @DisplayName("P0 Negative: Followers-only quote omits preview when quoted author is not a follower")
    void followersOnlyQuoteOmitsPreviewForNonFollower() {
        CreatePostRequest request = new CreatePostRequest(
                "Followers-only quote content",
                PostVisibility.FOLLOWERS,
                true,
                true,
                null,
                null,
                null,
                null
        );

        when(followRepository.existsById(new FollowId(3L, 1L))).thenReturn(false);

        postService.create(1L, request, null, null, 20L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertThat(event.recipientId()).isEqualTo(3L);
        assertThat(event.actorId()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo("POST_QUOTE");
        assertThat(event.targetId()).isEqualTo(999L);
        assertThat(event.contentPreview()).isNull();
    }
}
