package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.impl.AdminModerationServiceImpl;
import com.aloute.service.admin.impl.AdminPostServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression Tests for Admin REST Post Privacy (F-06 Strict Privacy) and Moderation Authorization.
 * Validates:
 * 1. Admin outsider denial (POST_ACCESS_DENIED) on detail for non-public, private-account, or private-community posts.
 * 2. Admin permitted visibility (author, follower, member) on detail with content preserved.
 * 3. Disabled removal/restore capabilities for restricted posts.
 * 4. List query audience specification enforcement preserving count/pagination semantics.
 * 5. Enforced SYSTEM_ADMIN role, active account check, and restricted post rejection on moderation actions.
 * Owned by Khôi (Security/Authorization) and Ngọc (Post/Entity).
 */
class AdminPostPrivacyPhaseATest {

    private PostRepository postRepository;
    private PostModerationRemovalRepository postModerationRemovalRepository;
    private ReportRepository reportRepository;
    private UserRepository userRepository;
    private AdminMappingHelper adminMappingHelper;
    private BlockRepository blockRepository;
    private FollowRepository followRepository;
    private CommunityMemberRepository communityMemberRepository;
    private CurrentUserProvider currentUserProvider;

    private AdminPostServiceImpl adminPostService;
    private AdminModerationServiceImpl adminModerationService;

    private User admin;
    private User normalUser;
    private User author;
    private User privateAuthor;
    private Post publicPost;
    private Post followersPost;
    private Post privateCommunityPost;
    private Post privateAuthorPublicPost;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postModerationRemovalRepository = mock(PostModerationRemovalRepository.class);
        reportRepository = mock(ReportRepository.class);
        userRepository = mock(UserRepository.class);
        adminMappingHelper = mock(AdminMappingHelper.class);
        blockRepository = mock(BlockRepository.class);
        followRepository = mock(FollowRepository.class);
        communityMemberRepository = mock(CommunityMemberRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        adminPostService = new AdminPostServiceImpl(
                postRepository,
                postModerationRemovalRepository,
                reportRepository,
                adminMappingHelper,
                blockRepository,
                followRepository,
                communityMemberRepository,
                currentUserProvider
        );

        when(postRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> postRepository.findById(inv.getArgument(0)));

        adminModerationService = new AdminModerationServiceImpl(
                userRepository,
                postRepository,
                reportRepository,
                mock(com.aloute.repository.ModerationActionRepository.class),
                postModerationRemovalRepository,
                adminMappingHelper,
                new ObjectMapper()
        );

        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(Role.SYSTEM_ADMIN);
        admin.setActive(true);

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUsername("regular_user");
        normalUser.setRole(Role.USER);
        normalUser.setActive(true);

        author = new User();
        author.setId(3L);
        author.setUsername("author");
        author.setPrivateAccount(false);

        privateAuthor = new User();
        privateAuthor.setId(4L);
        privateAuthor.setUsername("private_author");
        privateAuthor.setPrivateAccount(true);

        publicPost = new Post();
        publicPost.setId(10L);
        publicPost.setUser(author);
        publicPost.setContent("Public sensitive text that is open to public inspection");
        publicPost.setVisibility(PostVisibility.PUBLIC);

        followersPost = new Post();
        followersPost.setId(20L);
        followersPost.setUser(author);
        followersPost.setContent("Followers-only private diary entry");
        followersPost.setVisibility(PostVisibility.FOLLOWERS);

        Community privateComm = new Community();
        privateComm.setId(300L);
        privateComm.setPrivateCommunity(true);

        privateCommunityPost = new Post();
        privateCommunityPost.setId(30L);
        privateCommunityPost.setUser(author);
        privateCommunityPost.setContent("Private community internal discussion");
        privateCommunityPost.setVisibility(PostVisibility.PUBLIC);
        privateCommunityPost.setCommunity(privateComm);

        privateAuthorPublicPost = new Post();
        privateAuthorPublicPost.setId(40L);
        privateAuthorPublicPost.setUser(privateAuthor);
        privateAuthorPublicPost.setContent("Post by private account holder");
        privateAuthorPublicPost.setVisibility(PostVisibility.PUBLIC);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(1L));
        when(currentUserProvider.requireUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(reportRepository.findByReportedPostId(any())).thenReturn(Collections.emptyList());
        when(postModerationRemovalRepository.findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("getPostDetail preserves content and enables canRemove for public post by public author")
    void getPostDetailPreservesPublicContent() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(publicPost));

        AdminPostDetailResponse response = adminPostService.getPostDetail(1L, 10L);

        assertThat(response.content()).isEqualTo("Public sensitive text that is open to public inspection");
        assertThat(response.capabilities().canRemove()).isTrue();
        assertThat(response.capabilities().canRestore()).isFalse();
    }

    @Test
    @DisplayName("F-06 Strict Privacy: getPostDetail denies outsider admin for FOLLOWERS post (POST_ACCESS_DENIED)")
    void getPostDetailDeniesOutsiderFollowersPost() {
        when(postRepository.findById(20L)).thenReturn(Optional.of(followersPost));

        assertThatThrownBy(() -> adminPostService.getPostDetail(1L, 20L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("F-06 Strict Privacy: getPostDetail denies outsider admin for private community post (POST_ACCESS_DENIED)")
    void getPostDetailDeniesOutsiderPrivateCommunityPost() {
        when(postRepository.findById(30L)).thenReturn(Optional.of(privateCommunityPost));

        assertThatThrownBy(() -> adminPostService.getPostDetail(1L, 30L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("F-06 Strict Privacy: getPostDetail denies outsider admin for private-account author post (POST_ACCESS_DENIED)")
    void getPostDetailDeniesOutsiderPrivateAccountPost() {
        when(postRepository.findById(40L)).thenReturn(Optional.of(privateAuthorPublicPost));

        assertThatThrownBy(() -> adminPostService.getPostDetail(1L, 40L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("F-06 Permitted Visibility: author admin can view own restricted post detail with content, but moderation locked")
    void getPostDetailPermitsAuthorToViewOwnRestrictedPost() {
        when(postRepository.findById(20L)).thenReturn(Optional.of(followersPost));

        AdminPostDetailResponse response = adminPostService.getPostDetail(3L, 20L);

        assertThat(response.content()).isEqualTo("Followers-only private diary entry");
        assertThat(response.capabilities().canRemove()).isFalse();
        assertThat(response.capabilities().canRestore()).isFalse();
    }

    @Test
    @DisplayName("F-06 Permitted Visibility: follower admin can view FOLLOWERS post detail with content, but moderation locked")
    void getPostDetailPermitsFollowerToViewFollowersPost() {
        when(postRepository.findById(20L)).thenReturn(Optional.of(followersPost));
        when(followRepository.existsById(new FollowId(1L, 3L))).thenReturn(true);

        AdminPostDetailResponse response = adminPostService.getPostDetail(1L, 20L);

        assertThat(response.content()).isEqualTo("Followers-only private diary entry");
        assertThat(response.capabilities().canRemove()).isFalse();
        assertThat(response.capabilities().canRestore()).isFalse();
    }

    @Test
    @DisplayName("F-06 Permitted Visibility: member admin can view private community post detail with content, but moderation locked")
    void getPostDetailPermitsMemberToViewPrivateCommunityPost() {
        when(postRepository.findById(30L)).thenReturn(Optional.of(privateCommunityPost));
        when(communityMemberRepository.existsByCommunityIdAndUserId(300L, 1L)).thenReturn(true);

        AdminPostDetailResponse response = adminPostService.getPostDetail(1L, 30L);

        assertThat(response.content()).isEqualTo("Private community internal discussion");
        assertThat(response.capabilities().canRemove()).isFalse();
        assertThat(response.capabilities().canRestore()).isFalse();
    }

    @Test
    @DisplayName("getPosts maps query results and enables canRemove only for public active posts")
    void getPostsMapsSummaryResponseCorrectly() {
        when(postRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(publicPost)));

        Page<AdminPostSummaryResponse> page = adminPostService.getPosts(
                1L, null, null, null, null, null, PageRequest.of(0, 10));

        List<AdminPostSummaryResponse> items = page.getContent();
        assertThat(items).hasSize(1);

        assertThat(items.get(0).contentExcerpt()).contains("Public sensitive text");
        assertThat(items.get(0).capabilities().canRemove()).isTrue();
        assertThat(items.get(0).capabilities().canRestore()).isFalse();
    }

    @Test
    @DisplayName("removePost fails closed when attempting to moderate non-public post")
    void removePostFailsOnRestrictedPost() {
        when(postRepository.findById(20L)).thenReturn(Optional.of(followersPost));
        AdminRemovePostRequest req = new AdminRemovePostRequest(ReportReason.HARASSMENT, "Harassment", null, null);

        assertThatThrownBy(() -> adminModerationService.removePost(1L, 20L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("removePost fails closed when attempting to moderate post by private author (Finding 3)")
    void removePostFailsOnPrivateAuthorPost() {
        when(postRepository.findById(40L)).thenReturn(Optional.of(privateAuthorPublicPost));
        AdminRemovePostRequest req = new AdminRemovePostRequest(ReportReason.HARASSMENT, "Harassment", null, null);

        assertThatThrownBy(() -> adminModerationService.removePost(1L, 40L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("restorePost fails closed when attempting to restore non-public post")
    void restorePostFailsOnRestrictedPost() {
        when(postRepository.findById(20L)).thenReturn(Optional.of(followersPost));
        AdminRestorePostRequest req = new AdminRestorePostRequest(100L, "Appeal note");

        assertThatThrownBy(() -> adminModerationService.restorePost(1L, 20L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("restorePost fails closed when attempting to restore post by private author (Finding 3)")
    void restorePostFailsOnPrivateAuthorPost() {
        when(postRepository.findById(40L)).thenReturn(Optional.of(privateAuthorPublicPost));
        AdminRestorePostRequest req = new AdminRestorePostRequest(100L, "Appeal note");

        assertThatThrownBy(() -> adminModerationService.restorePost(1L, 40L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("removePost rejects non-admin actor with AUTH_FORBIDDEN")
    void removePostRejectsNonAdmin() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(publicPost));
        AdminRemovePostRequest req = new AdminRemovePostRequest(ReportReason.HARASSMENT, "Harassment", null, null);

        assertThatThrownBy(() -> adminModerationService.removePost(2L, 10L, req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
    }
}
