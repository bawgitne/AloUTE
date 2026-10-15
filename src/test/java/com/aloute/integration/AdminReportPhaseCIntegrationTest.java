package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.CreateReportRequest;
import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.ReportResponse;
import com.aloute.dto.response.admin.ReportDecisionResponse;
import com.aloute.dto.response.admin.ReportReviewResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.PostService;
import com.aloute.service.ReportService;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_report_phase_c_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminReportPhaseCIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;
    @Autowired private ReportService reportService;
    @Autowired private AdminReportService adminReportService;
    @Autowired private AdminModerationService adminModerationService;
    @Autowired private PostService postService;

    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private PostModerationRemovalRepository postModerationRemovalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    private User admin1;
    private User admin2;
    private User inactiveAdmin;
    private User normalUser;
    private User authorUser;
    private Community publicComm;
    private Community privateComm;
    private Post publicPost;
    private Post followersPost;
    private Post privateCommPost;
    private Post deletedPost;

    private Authentication admin1Auth;
    private Authentication normalUserAuth;

    private void assertInMemH2() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            assertThat(url).contains("jdbc:h2:mem");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify in-memory H2 database", ex);
        }
    }

    private User createUser(long suffix, String prefix, Role role, boolean active) {
        User u = new User();
        u.setUsername(prefix + "_" + suffix);
        u.setEmail(prefix + "_" + suffix + "@aloute.test");
        u.setPasswordHash("$2a$10$hashedPlaceholder");
        u.setVerified(true);
        u.setActive(active);
        u.setRole(role);
        u = userRepository.save(u);

        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + prefix);
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

    @BeforeEach
    void setUp() {
        assertInMemH2();

        txTemplate = new TransactionTemplate(transactionManager);

        cleanDatabase();

        long suffix = System.nanoTime();

        txTemplate.execute(status -> {
            admin1 = createUser(suffix, "admin1", Role.SYSTEM_ADMIN, true);
            admin2 = createUser(suffix, "admin2", Role.SYSTEM_ADMIN, true);
            inactiveAdmin = createUser(suffix, "inadmin", Role.SYSTEM_ADMIN, false);
            normalUser = createUser(suffix, "user", Role.USER, true);
            authorUser = createUser(suffix, "author", Role.USER, true);

            publicComm = new Community();
            publicComm.setName("Public Comm " + suffix);
            publicComm.setSlug("public-comm-" + suffix);
            publicComm.setOwner(admin1);
            publicComm.setPrivateCommunity(false);
            publicComm.setMemberCount(1L);
            publicComm = communityRepository.save(publicComm);

            privateComm = new Community();
            privateComm.setName("Private Comm " + suffix);
            privateComm.setSlug("private-comm-" + suffix);
            privateComm.setOwner(admin1);
            privateComm.setPrivateCommunity(true);
            privateComm.setMemberCount(1L);
            privateComm = communityRepository.save(privateComm);

            publicPost = createPost(authorUser, "Public content for reporting", PostVisibility.PUBLIC, publicComm, null);
            followersPost = createPost(authorUser, "Followers only content", PostVisibility.FOLLOWERS, null, null);
            privateCommPost = createPost(authorUser, "Private community post", PostVisibility.PUBLIC, privateComm, null);
            deletedPost = createPost(authorUser, "Already deleted content", PostVisibility.PUBLIC, publicComm, Instant.now());
            return null;
        });

        UserPrincipal admin1Principal = UserPrincipal.from(admin1);
        admin1Auth = new UsernamePasswordAuthenticationToken(admin1Principal, null, admin1Principal.getAuthorities());

        UserPrincipal normalPrincipal = UserPrincipal.from(normalUser);
        normalUserAuth = new UsernamePasswordAuthenticationToken(normalPrincipal, null, normalPrincipal.getAuthorities());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        assertInMemH2();
        postModerationRemovalRepository.deleteAll();
        moderationActionRepository.deleteAll();
        reportRepository.deleteAll();
        postRepository.deleteAll();
        communityMemberRepository.deleteAll();
        communityRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==========================================
    // 1. Target validation (ReportService.create)
    // ==========================================

    @Test
    @DisplayName("Report target validation: Nonexistent, deleted, self-user, or unreadable post targets fail closed; readable own-post succeeds")
    void reportTargetValidation() {
        // Nonexistent post -> 404 POST_NOT_FOUND
        CreateReportRequest nonexistentReq = new CreateReportRequest(null, 999999L, ReportReason.SPAM, "Spam");
        assertThatThrownBy(() -> reportService.create(normalUser.getId(), nonexistentReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));

        // Deleted post -> 404 POST_NOT_FOUND
        CreateReportRequest deletedReq = new CreateReportRequest(null, deletedPost.getId(), ReportReason.SPAM, "Spam");
        assertThatThrownBy(() -> reportService.create(normalUser.getId(), deletedReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));

        // User self-report on own user account -> 409 REPORT_SELF_NOT_ALLOWED
        CreateReportRequest selfUserReq = new CreateReportRequest(normalUser.getId(), null, ReportReason.HARASSMENT, "Self user report");
        assertThatThrownBy(() -> reportService.create(normalUser.getId(), selfUserReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_SELF_NOT_ALLOWED));

        // Author report on readable own post is permitted under canonical spec
        CreateReportRequest selfPostReq = new CreateReportRequest(null, publicPost.getId(), ReportReason.SPAM, "Self post report");
        ReportResponse ownPostResp = reportService.create(authorUser.getId(), selfPostReq);
        assertThat(ownPostResp.id()).isNotNull();
        assertThat(ownPostResp.reportedPostId()).isEqualTo(publicPost.getId());

        // Unreadable followers post (normalUser does NOT follow authorUser) -> 403 POST_ACCESS_DENIED
        CreateReportRequest followersReq = new CreateReportRequest(null, followersPost.getId(), ReportReason.SPAM, "Followers spam");
        assertThatThrownBy(() -> reportService.create(normalUser.getId(), followersReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Unreadable private community post (normalUser is NOT member of privateComm) -> 403 POST_ACCESS_DENIED
        CreateReportRequest privateCommReq = new CreateReportRequest(null, privateCommPost.getId(), ReportReason.SPAM, "Private comm spam");
        assertThatThrownBy(() -> reportService.create(normalUser.getId(), privateCommReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Valid report on public post succeeds
        CreateReportRequest validReq = new CreateReportRequest(null, publicPost.getId(), ReportReason.SPAM, "Valid spam report");
        ReportResponse response = reportService.create(normalUser.getId(), validReq);
        assertThat(response.id()).isNotNull();
        assertThat(response.reportedPostId()).isEqualTo(publicPost.getId());
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
    }

    // ==========================================
    // 2. Role Separation & Controlled 409 Mapping
    // ==========================================

    @Test
    @DisplayName("Role separation: Guest 401, USER 403, stale version 409 REPORT_VERSION_CONFLICT, and whitespace reasonDetail 422")
    void roleSeparationAndControlledErrors() throws Exception {
        Report r = new Report();
        r.setReporter(normalUser);
        r.setReportedPostId(publicPost.getId());
        r.setReason(ReportReason.SPAM);
        r.setStatus(ReportStatus.PENDING);
        final Report report = reportRepository.save(r);

        // Guest denied on REST (401 Unauthorized)
        mockMvc.perform(post("/api/admin/reports/" + report.getId() + "/start-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        // USER role denied on REST (403 Forbidden)
        mockMvc.perform(post("/api/admin/reports/" + report.getId() + "/start-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}")
                        .with(authentication(normalUserAuth))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Stale expectedVersion returns HTTP 409 REPORT_VERSION_CONFLICT
        mockMvc.perform(post("/api/admin/reports/" + report.getId() + "/start-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 999}")
                        .with(authentication(admin1Auth))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_VERSION_CONFLICT"));

        // Claim report to REVIEWED status for decision testing
        adminReportService.startReview(admin1.getId(), report.getId(), new AdminStartReportReviewRequest(0L));

        // Whitespace-only reasonDetail returns HTTP 422 VALIDATION_FAILED (cannot bypass @NotBlank)
        String whitespacePayload = """
                {
                    "expectedVersion": 1,
                    "decision": "RESOLVE",
                    "resolutionNote": "Valid resolution note exceeding ten chars",
                    "resolutionAction": {
                        "type": "POST_REMOVE",
                        "reasonCode": "SPAM",
                        "reasonDetail": "              "
                    }
                }
                """;
        mockMvc.perform(post("/api/admin/reports/" + report.getId() + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(whitespacePayload)
                        .with(authentication(admin1Auth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // Omitted / null reasonDetail falls back cleanly and succeeds
        String nullReasonDetailPayload = """
                {
                    "expectedVersion": 1,
                    "decision": "RESOLVE",
                    "resolutionNote": "Valid resolution note exceeding ten chars",
                    "resolutionAction": {
                        "type": "POST_REMOVE",
                        "reasonCode": "SPAM"
                    }
                }
                """;
        mockMvc.perform(post("/api/admin/reports/" + report.getId() + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullReasonDetailPayload)
                        .with(authentication(admin1Auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // Verify post deleted by fallback removal
        Post postAfterRemoval = postRepository.findById(publicPost.getId()).orElseThrow();
        assertThat(postAfterRemoval.getDeletedAt()).isNotNull();
    }

    // ==========================================
    // 3. State Transitions, Reviewer Ownership, & Version Concurrency
    // ==========================================

    @Test
    @DisplayName("State transitions: Strict PENDING->REVIEWED->RESOLVED/REJECTED, reviewer lock, and optimistic versioning")
    void stateTransitionsAndReviewerLock() {
        Report r = new Report();
        r.setReporter(normalUser);
        r.setReportedPostId(publicPost.getId());
        r.setReason(ReportReason.HARASSMENT);
        r.setStatus(ReportStatus.PENDING);
        r.setVersion(0L);
        final Report report = reportRepository.save(r);

        // 1. Cannot decide PENDING report
        AdminReportDecisionRequest prematureDecision = new AdminReportDecisionRequest(0L, "RESOLVE", "Note note note", null);
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), report.getId(), prematureDecision))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_INVALID_TRANSITION));

        // 2. startReview with stale expectedVersion fails 409
        AdminStartReportReviewRequest staleStart = new AdminStartReportReviewRequest(99L);
        assertThatThrownBy(() -> adminReportService.startReview(admin1.getId(), report.getId(), staleStart))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_VERSION_CONFLICT));

        // 3. startReview valid transition PENDING -> REVIEWED
        AdminStartReportReviewRequest validStart = new AdminStartReportReviewRequest(0L);
        ReportReviewResponse reviewResp = adminReportService.startReview(admin1.getId(), report.getId(), validStart);
        assertThat(reviewResp.status()).isEqualTo("REVIEWED");
        assertThat(reviewResp.reviewedBy()).isEqualTo(admin1.getId());
        assertThat(reviewResp.version()).isEqualTo(1L);

        // Persisted version equals incremented response version
        Report reloaded = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(reloaded.getReviewedBy().getId()).isEqualTo(admin1.getId());

        // 4. Repeated startReview by same reviewer rejected (REPORT_INVALID_TRANSITION)
        assertThatThrownBy(() -> adminReportService.startReview(admin1.getId(), report.getId(), new AdminStartReportReviewRequest(1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_INVALID_TRANSITION));

        // 5. startReview by another reviewer rejected (REPORT_ALREADY_IN_REVIEW)
        assertThatThrownBy(() -> adminReportService.startReview(admin2.getId(), report.getId(), new AdminStartReportReviewRequest(1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_ALREADY_IN_REVIEW));

        // 6. decideReport by unassigned reviewer rejected (REPORT_REVIEWER_REQUIRED)
        AdminReportDecisionRequest admin2Decision = new AdminReportDecisionRequest(1L, "RESOLVE", "Admin2 decision note", null);
        assertThatThrownBy(() -> adminReportService.decideReport(admin2.getId(), report.getId(), admin2Decision))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_REVIEWER_REQUIRED));

        // 7. decideReport with invalid decision string rejected
        AdminReportDecisionRequest badDecision = new AdminReportDecisionRequest(1L, "INVALID_DECISION", "Bad note here", null);
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), report.getId(), badDecision))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        // 8. decideReport with REJECT + resolutionAction rejected
        AdminReportDecisionRequest rejectWithAction = new AdminReportDecisionRequest(
                1L, "REJECT", "Reject note here",
                new AdminReportDecisionRequest.ResolutionAction("POST_REMOVE", ReportReason.SPAM, "detail here 123", null)
        );
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), report.getId(), rejectWithAction))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        // 9. decideReport with RESOLVE + unknown action type rejected
        AdminReportDecisionRequest unknownAction = new AdminReportDecisionRequest(
                1L, "RESOLVE", "Resolve note here",
                new AdminReportDecisionRequest.ResolutionAction("UNKNOWN_TYPE", ReportReason.SPAM, "detail here 123", null)
        );
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), report.getId(), unknownAction))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        // 10. decideReport stale version check
        AdminReportDecisionRequest staleDecide = new AdminReportDecisionRequest(0L, "RESOLVE", "Resolve note here", null);
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), report.getId(), staleDecide))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_VERSION_CONFLICT));

        // 11. Valid decideReport with RESOLVE and POST_REMOVE executes atomically
        AdminReportDecisionRequest.ResolutionAction removeAction = new AdminReportDecisionRequest.ResolutionAction(
                "POST_REMOVE", ReportReason.HARASSMENT, "Confirmed severe harassment", "Post removed by moderation"
        );
        AdminReportDecisionRequest validDecide = new AdminReportDecisionRequest(1L, "RESOLVE", "Violations confirmed note", removeAction);

        ReportDecisionResponse decideResp = adminReportService.decideReport(admin1.getId(), report.getId(), validDecide);
        assertThat(decideResp.status()).isEqualTo("RESOLVED");
        assertThat(decideResp.resolutionActionId()).isNotNull();
        assertThat(decideResp.version()).isEqualTo(2L);

        // Verify post deleted and moderation records created
        Report closedReport = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(closedReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(closedReport.getClosedAt()).isNotNull();
        assertThat(closedReport.getVersion()).isEqualTo(2L);

        Post removedPost = postRepository.findById(publicPost.getId()).orElseThrow();
        assertThat(removedPost.getDeletedAt()).isNotNull();

        PostModerationRemoval removal = postModerationRemovalRepository.findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(publicPost.getId()).orElseThrow();
        assertThat(removal.getRestoredAt()).isNull();

        ModerationAction modAction = moderationActionRepository.findById(decideResp.resolutionActionId()).orElseThrow();
        assertThat(modAction.getActionType()).isEqualTo("POST_REMOVE");
        assertThat(modAction.getRelatedReportId()).isEqualTo(report.getId());
        assertThat(modAction.getTargetId()).isEqualTo(publicPost.getId());

        // 12. Cannot startReview on already closed report
        assertThatThrownBy(() -> adminReportService.startReview(admin1.getId(), report.getId(), new AdminStartReportReviewRequest(2L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_INVALID_TRANSITION));
    }

    @Test
    @DisplayName("Target validation: User target report rejects POST_REMOVE action")
    void userTargetReportRejectsPostRemoveAction() {
        Report r = new Report();
        r.setReporter(normalUser);
        r.setReportedUser(authorUser);
        r.setReason(ReportReason.HARASSMENT);
        r.setStatus(ReportStatus.PENDING);
        r.setVersion(0L);
        final Report userReport = reportRepository.save(r);

        adminReportService.startReview(admin1.getId(), userReport.getId(), new AdminStartReportReviewRequest(0L));

        AdminReportDecisionRequest.ResolutionAction postRemoveAction = new AdminReportDecisionRequest.ResolutionAction(
                "POST_REMOVE", ReportReason.HARASSMENT, "Trying to remove post on user report", null
        );
        AdminReportDecisionRequest request = new AdminReportDecisionRequest(1L, "RESOLVE", "Resolved note here", postRemoveAction);

        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), userReport.getId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    // ==========================================
    // 4. Concurrency & Optimistic Locking Races
    // ==========================================

    @Test
    @DisplayName("Concurrency: Concurrent startReview attempts in separate transactions have exactly one winner")
    void concurrentStartReviewExactlyOneWinner() throws Exception {
        Report report = new Report();
        report.setReporter(normalUser);
        report.setReportedPostId(publicPost.getId());
        report.setReason(ReportReason.SPAM);
        report.setStatus(ReportStatus.PENDING);
        report.setVersion(0L);
        report = reportRepository.save(report);

        final Long reportId = report.getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger winners = new AtomicInteger(0);
            AtomicInteger conflicts = new AtomicInteger(0);

            Callable<Void> task1 = () -> {
                barrier.await();
                try {
                    adminReportService.startReview(admin1.getId(), reportId, new AdminStartReportReviewRequest(0L));
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.REPORT_VERSION_CONFLICT
                            || ex.getErrorCode() == ErrorCode.REPORT_ALREADY_IN_REVIEW
                            || ex.getErrorCode() == ErrorCode.REPORT_INVALID_TRANSITION) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Callable<Void> task2 = () -> {
                barrier.await();
                try {
                    adminReportService.startReview(admin2.getId(), reportId, new AdminStartReportReviewRequest(0L));
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.REPORT_VERSION_CONFLICT
                            || ex.getErrorCode() == ErrorCode.REPORT_ALREADY_IN_REVIEW
                            || ex.getErrorCode() == ErrorCode.REPORT_INVALID_TRANSITION) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Future<Void> f1 = executor.submit(task1);
            Future<Void> f2 = executor.submit(task2);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertThat(winners.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(1);

            Report finalReport = reportRepository.findById(reportId).orElseThrow();
            assertThat(finalReport.getStatus()).isEqualTo(ReportStatus.REVIEWED);
            assertThat(finalReport.getVersion()).isEqualTo(1L);
            assertThat(finalReport.getReviewedBy()).isNotNull();
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Concurrent decideReport attempts in separate transactions have exactly one winner and no duplicate removals")
    void concurrentDecideReportExactlyOneWinner() throws Exception {
        Report report = new Report();
        report.setReporter(normalUser);
        report.setReportedPostId(publicPost.getId());
        report.setReason(ReportReason.SPAM);
        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedBy(admin1);
        report.setReviewedAt(Instant.now());
        report.setVersion(1L);
        report = reportRepository.save(report);

        final Long reportId = report.getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger winners = new AtomicInteger(0);
            AtomicInteger conflicts = new AtomicInteger(0);

            Callable<Void> task1 = () -> {
                barrier.await();
                try {
                    AdminReportDecisionRequest.ResolutionAction action = new AdminReportDecisionRequest.ResolutionAction(
                            "POST_REMOVE", ReportReason.SPAM, "Spam removal reason 1", null
                    );
                    AdminReportDecisionRequest request = new AdminReportDecisionRequest(1L, "RESOLVE", "Decision resolution note 1", action);
                    adminReportService.decideReport(admin1.getId(), reportId, request);
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.REPORT_VERSION_CONFLICT
                            || ex.getErrorCode() == ErrorCode.REPORT_INVALID_TRANSITION
                            || ex.getErrorCode() == ErrorCode.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Callable<Void> task2 = () -> {
                barrier.await();
                try {
                    AdminReportDecisionRequest.ResolutionAction action = new AdminReportDecisionRequest.ResolutionAction(
                            "POST_REMOVE", ReportReason.SPAM, "Spam removal reason 2", null
                    );
                    AdminReportDecisionRequest request = new AdminReportDecisionRequest(1L, "RESOLVE", "Decision resolution note 2", action);
                    adminReportService.decideReport(admin1.getId(), reportId, request);
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.REPORT_VERSION_CONFLICT
                            || ex.getErrorCode() == ErrorCode.REPORT_INVALID_TRANSITION
                            || ex.getErrorCode() == ErrorCode.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Future<Void> f1 = executor.submit(task1);
            Future<Void> f2 = executor.submit(task2);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertThat(winners.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(1);

            Report finalReport = reportRepository.findById(reportId).orElseThrow();
            assertThat(finalReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
            assertThat(finalReport.getVersion()).isEqualTo(2L);
            assertThat(finalReport.getReviewedBy().getId()).isEqualTo(admin1.getId());

            // Exactly one removal record and one moderation action created
            long removalCount = postModerationRemovalRepository.count();
            assertThat(removalCount).isEqualTo(1L);

            long actionCount = moderationActionRepository.count();
            assertThat(actionCount).isEqualTo(1L);

            Post removedPost = postRepository.findById(publicPost.getId()).orElseThrow();
            assertThat(removedPost.getDeletedAt()).isNotNull();
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Concurrent removePost calls preserve invariant of exactly one active removal")
    void concurrentRemovePostPreservesActiveRemovalInvariant() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger winners = new AtomicInteger(0);
            AtomicInteger conflicts = new AtomicInteger(0);

            Callable<Void> removeTask1 = () -> {
                barrier.await();
                try {
                    adminModerationService.removePost(
                            admin1.getId(), publicPost.getId(),
                            new com.aloute.dto.request.admin.AdminRemovePostRequest(ReportReason.SPAM, "Spam removal reason 1", null, null)
                    );
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.CONFLICT
                            || ex.getErrorCode() == ErrorCode.POST_ALREADY_DELETED) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (DataIntegrityViolationException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Callable<Void> removeTask2 = () -> {
                barrier.await();
                try {
                    adminModerationService.removePost(
                            admin2.getId(), publicPost.getId(),
                            new com.aloute.dto.request.admin.AdminRemovePostRequest(ReportReason.SPAM, "Spam removal reason 2", null, null)
                    );
                    winners.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getErrorCode() == ErrorCode.CONFLICT
                            || ex.getErrorCode() == ErrorCode.POST_ALREADY_DELETED) {
                        conflicts.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (DataIntegrityViolationException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            };

            Future<Void> f1 = executor.submit(removeTask1);
            Future<Void> f2 = executor.submit(removeTask2);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertThat(winners.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(1);

            // Exactly one active removal in real database
            long activeCount = postModerationRemovalRepository.count();
            assertThat(activeCount).isEqualTo(1L);

            Post reloaded = postRepository.findById(publicPost.getId()).orElseThrow();
            assertThat(reloaded.getDeletedAt()).isNotNull();
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    // ==========================================
    // 5. Real DB Rollback Proof
    // ==========================================

    @Test
    @DisplayName("Rollback Proof: Failure occurring AFTER actual removal/audit writes rolls back Report, Post, and Moderation completely in fresh transaction")
    void realDbRollbackAfterRemovalWrites() {
        // Setup a report in REVIEWED status
        Report report = new Report();
        report.setReporter(normalUser);
        report.setReportedPostId(publicPost.getId());
        report.setReason(ReportReason.SPAM);
        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedBy(admin1);
        report.setReviewedAt(Instant.now());
        report.setVersion(1L);
        report = reportRepository.save(report);

        final Long reportId = report.getId();
        final Long postId = publicPost.getId();

        long initialRemovalCount = postModerationRemovalRepository.count();
        long initialActionCount = moderationActionRepository.count();

        // resolutionNote exceeding column length (1000 characters) will fail during saveAndFlush
        String oversizedNote = "A".repeat(1500);

        AdminReportDecisionRequest.ResolutionAction action = new AdminReportDecisionRequest.ResolutionAction(
                "POST_REMOVE", ReportReason.SPAM, "Valid spam reason", null
        );
        AdminReportDecisionRequest request = new AdminReportDecisionRequest(1L, "RESOLVE", oversizedNote, action);

        // decideReport executes removePost (which updates Post, saves ModerationAction, saves PostModerationRemoval),
        // and then fails during report saveAndFlush due to DB column length constraint on resolution_note
        assertThatThrownBy(() -> adminReportService.decideReport(admin1.getId(), reportId, request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("RESOLUTION_NOTE"));

        // VERIFY IN A FRESH TRANSACTION:
        txTemplate.execute(status -> {
            Report freshReport = reportRepository.findById(reportId).orElseThrow();
            Post freshPost = postRepository.findById(postId).orElseThrow();

            // Report state completely unchanged
            assertThat(freshReport.getStatus()).isEqualTo(ReportStatus.REVIEWED);
            assertThat(freshReport.getReviewedBy().getId()).isEqualTo(admin1.getId());
            assertThat(freshReport.getVersion()).isEqualTo(1L);
            assertThat(freshReport.getClosedAt()).isNull();
            assertThat(freshReport.getResolutionNote()).isNull();

            // Post deletedAt unchanged
            assertThat(freshPost.getDeletedAt()).isNull();

            // Moderation actions and removals completely rolled back (no partial writes)
            assertThat(postModerationRemovalRepository.count()).isEqualTo(initialRemovalCount);
            assertThat(moderationActionRepository.count()).isEqualTo(initialActionCount);

            return null;
        });
    }
}
