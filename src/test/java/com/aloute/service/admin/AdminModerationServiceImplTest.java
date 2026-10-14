package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.entity.*;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.admin.impl.AdminModerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminModerationServiceImplTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private ModerationActionRepository moderationActionRepository;
    private PostModerationRemovalRepository postModerationRemovalRepository;
    private AdminMappingHelper adminMappingHelper;
    private ObjectMapper objectMapper;
    private AdminModerationServiceImpl service;

    private User admin;
    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        moderationActionRepository = mock(ModerationActionRepository.class);
        postModerationRemovalRepository = mock(PostModerationRemovalRepository.class);
        adminMappingHelper = mock(AdminMappingHelper.class);
        objectMapper = new ObjectMapper();

        service = new AdminModerationServiceImpl(
                userRepository,
                postRepository,
                moderationActionRepository,
                postModerationRemovalRepository,
                adminMappingHelper,
                objectMapper
        );

        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");

        author = new User();
        author.setId(2L);
        author.setUsername("author");

        post = new Post();
        post.setId(10L);
        post.setUser(author);
        post.setContent("Violating content");
    }

    @Test
    @DisplayName("removePost successfully marks post as deleted and records moderation action and removal")
    void removePostSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postModerationRemovalRepository.existsByPostIdAndRestoredAtIsNull(10L)).thenReturn(false);

        ModerationAction savedAction = new ModerationAction();
        savedAction.setId(100L);
        when(moderationActionRepository.save(any(ModerationAction.class))).thenReturn(savedAction);

        PostModerationRemoval savedRemoval = new PostModerationRemoval();
        savedRemoval.setId(200L);
        when(postModerationRemovalRepository.save(any(PostModerationRemoval.class))).thenReturn(savedRemoval);

        AdminRemovePostRequest request = new AdminRemovePostRequest(
                ReportReason.SPAM,
                "Content is detected as automated spam",
                null,
                "This post violated spam policies"
        );

        PostRemovalActionResponse response = service.removePost(1L, 10L, request);

        assertThat(response.actionId()).isEqualTo(100L);
        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.actionType()).isEqualTo("POST_REMOVE");
        assertThat(response.contentState()).isEqualTo("ADMIN_REMOVED");
        assertThat(response.removalId()).isEqualTo(200L);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(postRepository).save(post);
        verify(moderationActionRepository).save(any(ModerationAction.class));
        verify(postModerationRemovalRepository).save(any(PostModerationRemoval.class));
    }

    @Test
    @DisplayName("restorePost restores admin-removed post successfully")
    void restorePostSuccess() {
        post.setDeletedAt(Instant.now());

        ModerationAction removeAction = new ModerationAction();
        removeAction.setId(100L);
        removeAction.setReasonCode(ReportReason.SPAM);

        PostModerationRemoval activeRemoval = new PostModerationRemoval();
        activeRemoval.setId(200L);
        activeRemoval.setPost(post);
        activeRemoval.setRemoveAction(removeAction);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postModerationRemovalRepository.findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(10L))
                .thenReturn(Optional.of(activeRemoval));

        ModerationAction savedRestoreAction = new ModerationAction();
        savedRestoreAction.setId(101L);
        when(moderationActionRepository.save(any(ModerationAction.class))).thenReturn(savedRestoreAction);

        AdminRestorePostRequest request = new AdminRestorePostRequest(
                200L,
                "False positive spam classification verified by appeal"
        );

        PostRestoreActionResponse response = service.restorePost(1L, 10L, request);

        assertThat(response.actionId()).isEqualTo(101L);
        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.actionType()).isEqualTo("POST_RESTORE");
        assertThat(response.contentState()).isEqualTo("ACTIVE");
        assertThat(response.restoredRemovalId()).isEqualTo(200L);

        assertThat(post.getDeletedAt()).isNull();
        assertThat(activeRemoval.getRestoredAt()).isNotNull();
        verify(postRepository).save(post);
        verify(postModerationRemovalRepository).save(activeRemoval);
    }

    @Test
    @DisplayName("restorePost rejects author-deleted post (Invariant 2 - Post Provenance)")
    void restoreAuthorDeletedPostFails() {
        post.setDeletedAt(Instant.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postModerationRemovalRepository.findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(10L))
                .thenReturn(Optional.empty());

        AdminRestorePostRequest request = new AdminRestorePostRequest(200L, "Trying to restore author-deleted post");

        assertThatThrownBy(() -> service.restorePost(1L, 10L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_REMOVAL_NOT_FOUND));

        verify(postRepository, never()).save(any(Post.class));
    }
}
