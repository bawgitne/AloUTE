package com.aloute.integration;

import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.admin.AdminPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real Database Integration Tests for Phase B Admin Post Hardening.
 * Covers:
 * - reportedOnly=true with EXISTS/subquery (no report-row multiplication), false/null no filter
 * - reports 0/1/multiple incl resolved history
 * - accurate content states (ACTIVE, ADMIN_REMOVED, AUTHOR_DELETED) and restored removal history
 * - composed filters and exact total pages/counts
 * - security: content search (q) cannot match restricted content (non-PUBLIC, private author, private community)
 * - bad sort/filter values validation
 * - REST and SSR delegation/parity
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminPostPhaseBIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminPostService adminPostService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private PostModerationRemovalRepository postModerationRemovalRepository;

    private User adminUser;
    private User normalUser;
    private User privateUser;
    private Community publicComm;
    private Community privateComm;
    private Authentication adminAuth;

    private User createUser(String username, String email, boolean isPrivate, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashedPlaceholder");
        u.setVerified(true);
        u.setPrivateAccount(isPrivate);
        u.setActive(true);
        u.setRole(role);
        u = userRepository.save(u);

        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + username);
        profileRepository.save(p);
        return u;
    }

    private Post createPost(User author, String content, PostVisibility visibility, Community community, Instant deletedAt) {
        Post p = new Post();
        p.setUser(author);
        p.setContent(content);
        p.setVisibility(visibility);
        p.setCommunity(community);
        p.setAllowReply(true);
        p.setAllowQuote(true);
        p.setDeletedAt(deletedAt);
        return postRepository.save(p);
    }

    private Report createReport(User reporter, Long postId, ReportStatus status, ReportReason reason) {
        Report r = new Report();
        r.setReporter(reporter);
        r.setReportedPostId(postId);
        r.setStatus(status);
        r.setReason(reason);
        r.setDescription("Report on post #" + postId);
        return reportRepository.save(r);
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        adminUser = createUser("admin_" + suffix, "admin_" + suffix + "@aloute.test", false, Role.SYSTEM_ADMIN);
        normalUser = createUser("user_" + suffix, "user_" + suffix + "@aloute.test", false, Role.USER);
        privateUser = createUser("priv_" + suffix, "priv_" + suffix + "@aloute.test", true, Role.USER);

        publicComm = new Community();
        publicComm.setName("Public Hub " + suffix);
        publicComm.setSlug("pub-hub-" + suffix);
        publicComm.setOwner(normalUser);
        publicComm.setPrivateCommunity(false);
        publicComm = communityRepository.save(publicComm);

        privateComm = new Community();
        privateComm.setName("Secret Lounge " + suffix);
        privateComm.setSlug("secret-lounge-" + suffix);
        privateComm.setOwner(normalUser);
        privateComm.setPrivateCommunity(true);
        privateComm = communityRepository.save(privateComm);

        UserPrincipal principal = UserPrincipal.from(adminUser);
        adminAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("DB: reportedOnly=true with EXISTS subquery handles 0/1/multiple reports without row multiplication")
    void reportedOnlyFiltersCorrectlyWithoutRowMultiplication() {
        // Post 1: 0 reports
        Post p0 = createPost(normalUser, "Post with zero reports", PostVisibility.PUBLIC, null, null);
        // Post 2: 1 pending report
        Post p1 = createPost(normalUser, "Post with one pending report", PostVisibility.PUBLIC, null, null);
        createReport(normalUser, p1.getId(), ReportStatus.PENDING, ReportReason.SPAM);
        // Post 3: 2 reports (1 RESOLVED, 1 REJECTED) -> multiple reports incl resolved history
        Post p2 = createPost(normalUser, "Post with multiple reports history", PostVisibility.PUBLIC, null, null);
        createReport(normalUser, p2.getId(), ReportStatus.RESOLVED, ReportReason.HARASSMENT);
        createReport(normalUser, p2.getId(), ReportStatus.REJECTED, ReportReason.HATE);

        // Query with reportedOnly = true
        Page<AdminPostSummaryResponse> reportedPage = adminPostService.getPosts(
                null, null, null, null, true, PageRequest.of(0, 20)
        );

        List<Long> ids = reportedPage.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(ids).contains(p1.getId(), p2.getId());
        assertThat(ids).doesNotContain(p0.getId());

        // Verify no row multiplication: p2 appears exactly once
        long countP2 = ids.stream().filter(id -> id.equals(p2.getId())).count();
        assertThat(countP2).isEqualTo(1L);

        // Query with reportedOnly = false -> includes p0
        Page<AdminPostSummaryResponse> allPage = adminPostService.getPosts(
                null, null, null, null, false, PageRequest.of(0, 20)
        );
        List<Long> allIds = allPage.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(allIds).contains(p0.getId(), p1.getId(), p2.getId());
    }

    @Test
    @DisplayName("DB: Content states ACTIVE, ADMIN_REMOVED, AUTHOR_DELETED with active removal provenance and restored history")
    void contentStatesAccurateWithRestoredHistory() {
        Instant now = Instant.now();

        // 1. Active post
        Post activePost = createPost(normalUser, "Active post", PostVisibility.PUBLIC, null, null);

        // 2. Admin removed post (deletedAt != null, active PostModerationRemoval with restoredAt == null)
        Post adminRemovedPost = createPost(normalUser, "Admin removed post", PostVisibility.PUBLIC, null, now);
        ModerationAction removeAction = new ModerationAction();
        removeAction.setActor(adminUser);
        removeAction.setTargetType(ModerationTargetType.POST);
        removeAction.setTargetId(adminRemovedPost.getId());
        removeAction.setActionType("POST_REMOVE");
        removeAction.setReasonCode(ReportReason.SPAM);
        removeAction.setReasonDetail("Removal for spam violation");
        removeAction = moderationActionRepository.save(removeAction);

        PostModerationRemoval removal = new PostModerationRemoval();
        removal.setPost(adminRemovedPost);
        removal.setRemoveAction(removeAction);
        removal.setRemovedAt(now);
        removal.setRestoredAt(null); // Active removal
        postModerationRemovalRepository.save(removal);

        // 3. Post restored then author-deleted: has removal with restoredAt != null, then author deleted it (deletedAt != null)
        Post restoredThenAuthorDeleted = createPost(normalUser, "Restored then author deleted", PostVisibility.PUBLIC, null, now);
        ModerationAction restoreAction = new ModerationAction();
        restoreAction.setActor(adminUser);
        restoreAction.setTargetType(ModerationTargetType.POST);
        restoreAction.setTargetId(restoredThenAuthorDeleted.getId());
        restoreAction.setActionType("POST_RESTORE");
        restoreAction.setReasonCode(ReportReason.SPAM);
        restoreAction.setReasonDetail("Restoration after appeal review");
        restoreAction = moderationActionRepository.save(restoreAction);

        PostModerationRemoval closedRemoval = new PostModerationRemoval();
        closedRemoval.setPost(restoredThenAuthorDeleted);
        closedRemoval.setRemoveAction(removeAction);
        closedRemoval.setRestoreAction(restoreAction);
        closedRemoval.setRemovedAt(now.minusSeconds(3600));
        closedRemoval.setRestoredAt(now.minusSeconds(1800)); // Previously restored!
        postModerationRemovalRepository.save(closedRemoval);

        // 4. Ordinary author-deleted post (no moderation removals)
        Post plainAuthorDeleted = createPost(normalUser, "Plain author deleted", PostVisibility.PUBLIC, null, now);

        // Test ACTIVE filter
        Page<AdminPostSummaryResponse> activeList = adminPostService.getPosts(
                null, null, null, "ACTIVE", null, PageRequest.of(0, 20)
        );
        List<Long> activeIds = activeList.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(activeIds).contains(activePost.getId());
        assertThat(activeIds).doesNotContain(adminRemovedPost.getId(), restoredThenAuthorDeleted.getId(), plainAuthorDeleted.getId());

        // Test ADMIN_REMOVED filter
        Page<AdminPostSummaryResponse> removedList = adminPostService.getPosts(
                null, null, null, "ADMIN_REMOVED", null, PageRequest.of(0, 20)
        );
        List<Long> removedIds = removedList.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(removedIds).contains(adminRemovedPost.getId());
        assertThat(removedIds).doesNotContain(activePost.getId(), restoredThenAuthorDeleted.getId(), plainAuthorDeleted.getId());

        // Test AUTHOR_DELETED filter: MUST include plainAuthorDeleted AND restoredThenAuthorDeleted, but NOT adminRemovedPost
        Page<AdminPostSummaryResponse> authorDeletedList = adminPostService.getPosts(
                null, null, null, "AUTHOR_DELETED", null, PageRequest.of(0, 20)
        );
        List<Long> authorDeletedIds = authorDeletedList.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(authorDeletedIds).contains(plainAuthorDeleted.getId(), restoredThenAuthorDeleted.getId());
        assertThat(authorDeletedIds).doesNotContain(adminRemovedPost.getId(), activePost.getId());
    }

    @Test
    @DisplayName("DB Security: Content search (q) CANNOT match restricted content (non-PUBLIC, private author, private community)")
    void queryCannotMatchRestrictedContent() {
        String keyword = "confidentialkey";

        // 1. Unrestricted public post -> MUST match
        Post publicMatch = createPost(normalUser, "Discussion on " + keyword + " in tech", PostVisibility.PUBLIC, null, null);

        // 2. Followers post -> MUST NOT match q
        Post followersPost = createPost(normalUser, "Followers only with " + keyword, PostVisibility.FOLLOWERS, null, null);

        // 3. Private author post -> MUST NOT match q
        Post privateAuthorPost = createPost(privateUser, "Private author with " + keyword, PostVisibility.PUBLIC, null, null);

        // 4. Private community post -> MUST NOT match q
        Post privateCommPost = createPost(normalUser, "Private community with " + keyword, PostVisibility.PUBLIC, privateComm, null);

        // Search with query = "confidentialkey"
        Page<AdminPostSummaryResponse> searchResults = adminPostService.getPosts(
                keyword, null, null, null, null, PageRequest.of(0, 20)
        );

        List<Long> searchIds = searchResults.getContent().stream().map(AdminPostSummaryResponse::id).toList();
        assertThat(searchIds).contains(publicMatch.getId());
        assertThat(searchIds).doesNotContain(followersPost.getId(), privateAuthorPost.getId(), privateCommPost.getId());

        // Browse without query (q=null) -> all posts are returned, and restricted ones are redacted
        Page<AdminPostSummaryResponse> browseResults = adminPostService.getPosts(
                null, null, null, null, null, PageRequest.of(0, 50)
        );
        List<AdminPostSummaryResponse> browseItems = browseResults.getContent();

        AdminPostSummaryResponse followersResp = browseItems.stream().filter(p -> p.id().equals(followersPost.getId())).findFirst().orElseThrow();
        assertThat(followersResp.contentExcerpt()).isEqualTo("[REDACTED - RESTRICTED CONTENT]");
        assertThat(followersResp.capabilities().canRemove()).isFalse();

        AdminPostSummaryResponse privAuthorResp = browseItems.stream().filter(p -> p.id().equals(privateAuthorPost.getId())).findFirst().orElseThrow();
        assertThat(privAuthorResp.contentExcerpt()).isEqualTo("[REDACTED - RESTRICTED CONTENT]");
        assertThat(privAuthorResp.capabilities().canRemove()).isFalse();

        AdminPostSummaryResponse privCommResp = browseItems.stream().filter(p -> p.id().equals(privateCommPost.getId())).findFirst().orElseThrow();
        assertThat(privCommResp.contentExcerpt()).isEqualTo("[REDACTED - RESTRICTED CONTENT]");
        assertThat(privCommResp.capabilities().canRemove()).isFalse();
    }

    @Test
    @DisplayName("REST: Bad sort property, direction, format, contentState, or negative IDs return 400 Bad Request")
    void badSortAndFilterParametersReturn400() throws Exception {
        // 1. Invalid sort property
        mockMvc.perform(get("/api/admin/posts")
                        .param("sort", "maliciousProperty,desc")
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 2. Invalid sort direction
        mockMvc.perform(get("/api/admin/posts")
                        .param("sort", "createdAt,ascending")
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 3. Invalid sort format
        mockMvc.perform(get("/api/admin/posts")
                        .param("sort", "createdAt,desc,extra")
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 3b. Malformed sort inputs: ',', ',,', 'createdAt,', 'createdAt,desc,' return 400 in REST
        for (String malformed : List.of(",", ",,", "createdAt,", "createdAt,desc,")) {
            mockMvc.perform(get("/api/admin/posts")
                            .param("sort", malformed)
                            .with(authentication(adminAuth)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }

        // 3c. SSR safely handles malformed sort without 500 (renders error state)
        mockMvc.perform(get("/admin/posts")
                        .param("sort", ",")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(model().attributeExists("errorMessage"));

        mockMvc.perform(get("/admin/posts")
                        .param("sort", "createdAt,")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(model().attributeExists("errorMessage"));

        // 4. Invalid contentState
        mockMvc.perform(get("/api/admin/posts")
                        .param("contentState", "INVALID_STATE")
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 5. Negative authorId
        mockMvc.perform(get("/api/admin/posts")
                        .param("authorId", "-5")
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("REST & SSR Parity: both endpoints handle reportedOnly filter consistently and report same items")
    void restAndSsrParityForReportedOnly() throws Exception {
        Post pReported = createPost(normalUser, "Parity test reported post", PostVisibility.PUBLIC, null, null);
        createReport(normalUser, pReported.getId(), ReportStatus.PENDING, ReportReason.SPAM);

        Post pClean = createPost(normalUser, "Parity test clean post", PostVisibility.PUBLIC, null, null);

        // REST API
        mockMvc.perform(get("/api/admin/posts")
                        .param("reportedOnly", "true")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(pReported.getId().intValue())))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(pClean.getId().intValue()))));

        // SSR Page
        mockMvc.perform(get("/admin/posts")
                        .param("reportedOnly", "true")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(model().attribute("reportedOnly", true))
                .andExpect(content().string(containsString("#" + pReported.getId())))
                .andExpect(content().string(not(containsString("#" + pClean.getId()))));
    }

    @Test
    @DisplayName("DB Pagination: Composed author, community, contentState, and reportedOnly filters assert exact totalElements, totalPages, and no row duplicates across page size 1 and 2")
    void composedFiltersExactPaginationTotalElementsAndPages() {
        // Create 3 posts matching criteria: author=normalUser, community=publicComm, contentState=ACTIVE, reportedOnly=true
        // Post 1: has 1 report
        Post post1 = createPost(normalUser, "Composed match post 1", PostVisibility.PUBLIC, publicComm, null);
        createReport(normalUser, post1.getId(), ReportStatus.PENDING, ReportReason.SPAM);

        // Post 2: has 2 reports (multiple reports history)
        Post post2 = createPost(normalUser, "Composed match post 2", PostVisibility.PUBLIC, publicComm, null);
        createReport(normalUser, post2.getId(), ReportStatus.RESOLVED, ReportReason.HARASSMENT);
        createReport(normalUser, post2.getId(), ReportStatus.REJECTED, ReportReason.HATE);

        // Post 3: has 3 reports
        Post post3 = createPost(normalUser, "Composed match post 3", PostVisibility.PUBLIC, publicComm, null);
        createReport(normalUser, post3.getId(), ReportStatus.PENDING, ReportReason.MISINFORMATION);
        createReport(normalUser, post3.getId(), ReportStatus.REVIEWED, ReportReason.SPAM);
        createReport(normalUser, post3.getId(), ReportStatus.RESOLVED, ReportReason.OTHER);

        // Non-matching post A: author=normalUser, community=publicComm, but 0 reports
        createPost(normalUser, "Non-match zero reports", PostVisibility.PUBLIC, publicComm, null);

        // Non-matching post B: author=privateUser, community=publicComm, has report
        Post nonMatchDifferentAuthor = createPost(privateUser, "Non-match diff author", PostVisibility.PUBLIC, publicComm, null);
        createReport(normalUser, nonMatchDifferentAuthor.getId(), ReportStatus.PENDING, ReportReason.SPAM);

        // Non-matching post C: author=normalUser, community=privateComm, has report
        Post nonMatchDifferentComm = createPost(normalUser, "Non-match diff comm", PostVisibility.PUBLIC, privateComm, null);
        createReport(normalUser, nonMatchDifferentComm.getId(), ReportStatus.PENDING, ReportReason.SPAM);

        // Non-matching post D: author=normalUser, community=publicComm, has report, but deleted (contentState != ACTIVE)
        Post nonMatchDeleted = createPost(normalUser, "Non-match deleted", PostVisibility.PUBLIC, publicComm, Instant.now());
        createReport(normalUser, nonMatchDeleted.getId(), ReportStatus.PENDING, ReportReason.SPAM);

        // 1. Query with Page Size = 1 -> should have totalElements=3, totalPages=3
        Page<AdminPostSummaryResponse> pSize1Page0 = adminPostService.getPosts(
                null, normalUser.getId(), publicComm.getId(), "ACTIVE", true, PageRequest.of(0, 1)
        );
        assertThat(pSize1Page0.getTotalElements()).isEqualTo(3L);
        assertThat(pSize1Page0.getTotalPages()).isEqualTo(3);
        assertThat(pSize1Page0.getContent()).hasSize(1);
        assertThat(pSize1Page0.getNumber()).isEqualTo(0);

        Page<AdminPostSummaryResponse> pSize1Page1 = adminPostService.getPosts(
                null, normalUser.getId(), publicComm.getId(), "ACTIVE", true, PageRequest.of(1, 1)
        );
        assertThat(pSize1Page1.getTotalElements()).isEqualTo(3L);
        assertThat(pSize1Page1.getTotalPages()).isEqualTo(3);
        assertThat(pSize1Page1.getContent()).hasSize(1);
        assertThat(pSize1Page1.getNumber()).isEqualTo(1);

        Page<AdminPostSummaryResponse> pSize1Page2 = adminPostService.getPosts(
                null, normalUser.getId(), publicComm.getId(), "ACTIVE", true, PageRequest.of(2, 1)
        );
        assertThat(pSize1Page2.getTotalElements()).isEqualTo(3L);
        assertThat(pSize1Page2.getTotalPages()).isEqualTo(3);
        assertThat(pSize1Page2.getContent()).hasSize(1);
        assertThat(pSize1Page2.getNumber()).isEqualTo(2);

        // Ensure distinct items across pages (no duplicate rows)
        Long id0 = pSize1Page0.getContent().get(0).id();
        Long id1 = pSize1Page1.getContent().get(0).id();
        Long id2 = pSize1Page2.getContent().get(0).id();
        assertThat(List.of(id0, id1, id2)).containsExactlyInAnyOrder(post1.getId(), post2.getId(), post3.getId());

        // 2. Query with Page Size = 2 -> should have totalElements=3, totalPages=2
        Page<AdminPostSummaryResponse> pSize2Page0 = adminPostService.getPosts(
                null, normalUser.getId(), publicComm.getId(), "ACTIVE", true, PageRequest.of(0, 2)
        );
        assertThat(pSize2Page0.getTotalElements()).isEqualTo(3L);
        assertThat(pSize2Page0.getTotalPages()).isEqualTo(2);
        assertThat(pSize2Page0.getContent()).hasSize(2);

        Page<AdminPostSummaryResponse> pSize2Page1 = adminPostService.getPosts(
                null, normalUser.getId(), publicComm.getId(), "ACTIVE", true, PageRequest.of(1, 2)
        );
        assertThat(pSize2Page1.getTotalElements()).isEqualTo(3L);
        assertThat(pSize2Page1.getTotalPages()).isEqualTo(2);
        assertThat(pSize2Page1.getContent()).hasSize(1);

        // Verify all 3 posts are present without duplicates across page 0 and 1
        List<Long> page2Combined = new java.util.ArrayList<>();
        pSize2Page0.getContent().forEach(p -> page2Combined.add(p.id()));
        pSize2Page1.getContent().forEach(p -> page2Combined.add(p.id()));
        assertThat(page2Combined).containsExactlyInAnyOrder(post1.getId(), post2.getId(), post3.getId());
    }
}
