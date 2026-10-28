package com.aloute.config;

import com.aloute.entity.Bookmark;
import com.aloute.entity.Community;
import com.aloute.entity.CommunityMember;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.Conversation;
import com.aloute.entity.ConversationMember;
import com.aloute.entity.ConversationType;
import com.aloute.entity.Follow;
import com.aloute.entity.FollowId;
import com.aloute.entity.MediaAsset;
import com.aloute.entity.MediaPurpose;
import com.aloute.entity.MediaType;
import com.aloute.entity.Message;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Notification;
import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.PollVote;
import com.aloute.entity.Post;
import com.aloute.entity.PostLike;
import com.aloute.entity.PostMedia;
import com.aloute.entity.PostTopic;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Profile;
import com.aloute.entity.Report;
import com.aloute.entity.ReportStatus;
import com.aloute.entity.Repost;
import com.aloute.entity.Topic;
import com.aloute.entity.User;
import com.aloute.entity.UserNotificationSetting;
import com.aloute.entity.UserTopic;
import com.aloute.repository.BookmarkRepository;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.repository.ConversationRepository;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.MessageRepository;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.PollOptionRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PollVoteRepository;
import com.aloute.repository.PostLikeRepository;
import com.aloute.repository.PostMediaRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.PostTopicRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.RepostRepository;
import com.aloute.repository.TopicRepository;
import com.aloute.repository.UserNotificationSettingRepository;
import com.aloute.repository.UserRepository;
import com.aloute.repository.UserTopicRepository;
import com.aloute.security.authorization.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Rich sample data for the local Thymeleaf web app (H2, dev profile only).
 * Never runs under the test profile, so integration tests keep their own fixtures.
 */
@Component
@org.springframework.context.annotation.Profile("dev & !test")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DevSampleDataInitializer implements CommandLineRunner {

    private static final String[][] TOPICS = {
            {"Học tập", "hoc-tap"},
            {"Nghiên cứu", "nghien-cuu"},
            {"UTE Connect", "ute-connect"},
            {"CLB Khoa", "clb-khoa"},
            {"Đồ án tốt nghiệp", "do-an-tot-nghiep"},
            {"Lập trình", "lap-trinh"},
            {"Spring Boot", "spring-boot"},
            {"Flutter", "flutter"},
            {"AI & Machine Learning", "ai-ml"},
            {"Thực tập & Việc làm", "thuc-tap"},
            {"Học bổng", "hoc-bong"},
            {"Đời sống sinh viên", "doi-song-sinh-vien"},
            {"Thể thao", "the-thao"},
            {"Âm nhạc", "am-nhac"},
            {"Khởi nghiệp", "khoi-nghiep"}
    };

    private static final int[] POSTS_WITH_MEDIA = {2, 10, 13, 18, 24, 30, 36, 46, 55, 64, 70, 82, 88, 95};

    private static final String[] MESSAGES_STUDY = {
            "Mọi người ơi, tối nay họp nhóm lúc 20h nhé.",
            "Mình đã cập nhật tài liệu và sơ đồ CSDL lên Drive rồi.",
            "Phần API mình làm xong endpoint đăng nhập rồi nha.",
            "Bạn nào rảnh review giúp mình đoạn service xử lý like với.",
            "Mình vừa push code lên nhánh develop, mọi người pull về chạy thử nhé.",
            "Có lỗi gì mọi người ghi vào mục Issues giúp mình.",
            "Tối mai mình bận ca làm thêm, họp lùi 30 phút được không?",
            "Mình gửi link Google Meet trong phần mô tả nhóm nhé.",
            "Cả nhà nhớ cập nhật tiến độ trước 22h hôm nay.",
            "Ok chốt kế hoạch vậy nhé, cảm ơn mọi người!"
    };

    private static final String[] MESSAGES_EVENT = {
            "Poster đêm nhạc đã xong chưa mọi người?",
            "Mình vừa duyệt xong mẫu banner, màu ổn lắm.",
            "Ban âm thanh cần thêm hai bạn hỗ trợ chiều thứ Sáu.",
            "Mình sẽ lên bài đăng trên AloUTE vào 19h tối nay.",
            "Danh sách tiết mục đã chốt, có 12 tiết mục nhé.",
            "Quà tặng cho khán giả đến sớm mình đã chuẩn bị xong.",
            "Ai phụ trách check danh sách đăng ký giúp mình với.",
            "Hẹn mọi người 17h có mặt tại sảnh A để setup.",
            "Đêm nay nhớ mang áo CLB nhé.",
            "Tuyệt vời, chúc chương trình thành công!"
    };

    private static final String[] MESSAGES_FRIEND = {
            "Bạn rảnh không, mình hỏi chút về bài tập nhóm.",
            "Tối nay mình học thư viện, bạn có muốn đi cùng không?",
            "Mình vừa gửi file qua mail, bạn check giúp mình nhé.",
            "Ok bạn, hẹn gặp ở canteen lúc 11h30.",
            "Cảm ơn bạn nhiều, mình vừa nhận được rồi.",
            "Cuối tuần này có kế hoạch gì chưa?",
            "Mình đang cân nhắc đăng ký thêm môn tự chọn, bạn thấy sao?",
            "Nghe ổn đó, mình cũng đang định làm vậy.",
            "Vậy chốt nhé, tối gặp.",
            "Bạn nhớ giữ sức khỏe, dạo này deadline nhiều quá."
    };

    private static final String[] MESSAGES_WORK = {
            "Em chào anh chị, em đã gửi CV qua email ạ.",
            "Mình đã nhận được hồ sơ của bạn, hẹn phỏng vấn thứ Năm nhé.",
            "Em cảm ơn ạ, em sẽ chuẩn bị kỹ.",
            "Bạn cho mình hỏi vị trí này có hỗ trợ làm hybrid không ạ?",
            "Công ty mình linh hoạt hai ngày remote mỗi tuần nhé.",
            "Thời gian thực tập tối thiểu là ba tháng đúng không ạ?",
            "Đúng rồi, ưu tiên bạn có thể làm bốn ngày mỗi tuần.",
            "Em sắp xếp được ạ, em cảm ơn anh chị.",
            "Mình gửi lịch hẹn qua email, bạn xác nhận giúp nhé.",
            "Cảm ơn bạn, hẹn gặp lại."
    };

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TopicRepository topicRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostRepository postRepository;
    private final PostTopicRepository postTopicRepository;
    private final PostLikeRepository postLikeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RepostRepository repostRepository;
    private final FollowRepository followRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PostMediaRepository postMediaRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final UserNotificationSettingRepository notificationSettingRepository;
    private final UserTopicRepository userTopicRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.core.env.Environment environment;

    @Override
    public void run(String... args) {
        if (environment.getProperty(ProductionProfileGuard.PRODUCTION_MODE_PROPERTY, Boolean.class, false)) {
            return;
        }
        if (postRepository.count() > 0) {
            log.info("Dev sample data already present; skipping seed.");
            return;
        }
        if (userRepository.findByUsernameIgnoreCase("admin").isEmpty()) {
            log.warn("Base dev accounts are missing; skipping dev sample data seed.");
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> seed());
    }

    private void seed() {
        SeedContext ctx = new SeedContext(Instant.now());
        seedUsers(ctx);
        seedTopics(ctx);
        seedCommunities(ctx);
        seedRootPosts(ctx);
        seedReplies(ctx);
        seedQuotes(ctx);
        seedPolls(ctx);
        seedEngagement(ctx);
        seedFollows(ctx);
        seedConversations(ctx);
        seedReportsAndModeration(ctx);
        seedNotificationSettings(ctx);
        seedUserTopics(ctx);
        seedNotifications(ctx);
        finalizeCounters(ctx);

        entityManager.flush();
        backdateTimestamps(ctx);

        log.info("Dev sample data seeded: {} users, {} topics, {} communities, {} root posts, {} replies, "
                        + "{} conversations, {} messages, {} notifications, {} reports.",
                ctx.allUsers.size(), ctx.topics.size(), ctx.communities.size(), ctx.roots.size(),
                ctx.replies.size(), ctx.conversationCount, ctx.messageTimes.size(),
                ctx.notificationTimes.size(), ctx.reportTimes.size());
        log.info("Demo login: user@aloute.local / User@123 (admin@aloute.local / Admin@123). "
                + "All extra sample users use password {}", DevTestDataInitializer.DEMO_STUDENT_PASSWORD);
    }

    private void seedUsers(SeedContext ctx) {
        for (String base : List.of("admin", "user", "demoan", "demobinh")) {
            userRepository.findByUsernameIgnoreCase(base)
                    .ifPresent(u -> {
                        ctx.users.put(base, u);
                        ctx.allUsers.add(u);
                    });
        }
        if (!ctx.users.containsKey("admin") || !ctx.users.containsKey("user")) {
            throw new IllegalStateException("Base dev accounts must exist before sample data seeding");
        }

        String encodedPassword = passwordEncoder.encode(DevTestDataInitializer.DEMO_STUDENT_PASSWORD);
        Map<String, DevSeedContent.DemoUser> demoUsers = new LinkedHashMap<>();
        for (DevSeedContent.DemoUser demo : DevSeedContent.USERS) {
            demoUsers.put(demo.username(), demo);
        }

        for (DevSeedContent.DemoUser demo : DevSeedContent.USERS) {
            User user = userRepository.findByUsernameIgnoreCase(demo.username()).orElseGet(() -> {
                User created = new User();
                created.setUsername(demo.username());
                created.setEmail(demo.username() + "@aloute.test");
                created.setPasswordHash(encodedPassword);
                created.setVerified(demo.verified());
                created.setPrivateAccount(demo.privateAccount());
                created.setActive(true);
                created.setRole(Role.USER);
                return userRepository.save(created);
            });
            ctx.users.put(demo.username(), user);
            ctx.allUsers.add(user);
        }

        for (User user : ctx.allUsers) {
            DevSeedContent.DemoUser demo = demoUsers.get(user.getUsername().toLowerCase(Locale.ROOT));
            Profile profile = profileRepository.findById(user.getId()).orElseGet(Profile::new);
            boolean fresh = profile.getUserId() == null;
            profile.setUser(user);
            if (fresh || blank(profile.getDisplayName())) {
                profile.setDisplayName(displayNameFor(user.getUsername(), demo));
            }
            if (fresh || blank(profile.getBio())) {
                profile.setBio(demo != null ? demo.bio() : "Thành viên mạng xã hội AloUTE.");
            }
            if (fresh || blank(profile.getAvatarUrl())) {
                profile.setAvatarUrl(demo != null ? demo.avatarUrl()
                        : "https://i.pravatar.cc/160?u=aloute-" + user.getUsername());
            }
            if (demo != null && demo.coverUrl() != null && (fresh || blank(profile.getCoverUrl()))) {
                profile.setCoverUrl(demo.coverUrl());
            }
            profileRepository.save(profile);
        }
    }

    private void seedTopics(SeedContext ctx) {
        for (String[] definition : TOPICS) {
            Topic topic = topicRepository.findBySlug(definition[1]).orElseGet(() -> {
                Topic created = new Topic();
                created.setName(definition[0]);
                created.setSlug(definition[1]);
                return topicRepository.save(created);
            });
            ctx.topics.put(definition[1], topic);
        }
    }

    private void seedCommunities(SeedContext ctx) {
        for (DevSeedContent.DemoCommunity definition : DevSeedContent.COMMUNITIES) {
            User owner = ctx.users.get(definition.ownerUsername());
            if (owner == null) {
                log.warn("Skipping community {} because owner {} is missing", definition.slug(), definition.ownerUsername());
                continue;
            }
            Community community = new Community();
            community.setOwner(owner);
            community.setName(definition.name());
            community.setSlug(definition.slug());
            community.setDescription(definition.description());
            community.setAvatarUrl(definition.avatarUrl());
            community.setCoverUrl(definition.coverUrl());
            community.setPrivateCommunity(definition.privateCommunity());
            community.setMemberCount(0);
            community.setPostCount(0);
            communityRepository.save(community);

            Set<Long> memberIds = new HashSet<>();
            List<CommunityMember> members = new ArrayList<>();
            for (String entry : definition.members()) {
                String[] parts = entry.split(":");
                User user = ctx.users.get(parts[0]);
                if (user == null) {
                    log.warn("Skipping community member {} of {}", entry, definition.slug());
                    continue;
                }
                CommunityRole role;
                try {
                    role = parts.length > 1 ? CommunityRole.valueOf(parts[1]) : CommunityRole.MEMBER;
                } catch (IllegalArgumentException ex) {
                    role = CommunityRole.MEMBER;
                }
                members.add(new CommunityMember(community, user, role));
                memberIds.add(user.getId());
            }
            if (!memberIds.contains(owner.getId())) {
                members.add(new CommunityMember(community, owner, CommunityRole.ADMIN));
                memberIds.add(owner.getId());
            }
            communityMemberRepository.saveAll(members);
            community.setMemberCount(members.size());
            communityRepository.save(community);

            ctx.communities.put(definition.slug(), community);
            ctx.communityMembers.put(community.getId(), memberIds);
        }
    }

    private void seedRootPosts(SeedContext ctx) {
        List<Post> roots = new ArrayList<>();
        for (int i = 0; i < DevSeedContent.POSTS.size(); i++) {
            DevSeedContent.DemoPost definition = DevSeedContent.POSTS.get(i);
            User author = ctx.users.get(definition.authorUsername());
            Community community = definition.communitySlug().isBlank()
                    ? null : ctx.communities.get(definition.communitySlug());
            if (author == null) {
                log.warn("Sample post {} references missing author {}; using admin", i, definition.authorUsername());
                author = ctx.users.get("admin");
            }
            if (community != null
                    && !ctx.communityMembers.getOrDefault(community.getId(), Set.of()).contains(author.getId())) {
                log.warn("Post {} author {} is not a member of {}; using community owner",
                        i, author.getUsername(), community.getSlug());
                author = community.getOwner();
            }
            Post post = new Post();
            post.setUser(author);
            post.setContent(definition.content());
            post.setCommunity(community);
            post.setVisibility(visibilityFor(i));
            post.setAllowReply(true);
            post.setAllowQuote(true);
            post.setViewCount(35 + (i * 41L) % 860);
            roots.add(post);
        }
        List<Post> saved = postRepository.saveAll(roots);
        ctx.roots.addAll(saved);

        List<Instant> times = assignRootTimes(ctx, saved);

        List<PostTopic> postTopics = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            Post post = saved.get(i);
            for (Topic topic : topicsFor(ctx, DevSeedContent.POSTS.get(i))) {
                postTopics.add(new PostTopic(post, topic));
            }
            ctx.postTimes.put(post.getId(), times.get(i));
        }
        if (!postTopics.isEmpty()) {
            postTopicRepository.saveAll(postTopics);
        }
        seedPostMedia(ctx, saved);
    }

    private List<Instant> assignRootTimes(SeedContext ctx, List<Post> posts) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(101));
        List<Instant> times = new ArrayList<>(Collections.nCopies(posts.size(), ctx.now));
        for (int rank = 0; rank < order.size(); rank++) {
            Instant time = ctx.now
                    .minus(15, ChronoUnit.MINUTES)
                    .minusSeconds(rank * 3L * 3600L)
                    .minusSeconds((rank * 53L) % 1800L);
            times.set(order.get(rank), time);
        }
        return times;
    }

    private void seedPostMedia(SeedContext ctx, List<Post> roots) {
        List<MediaAsset> assets = new ArrayList<>();
        List<Post> targetPosts = new ArrayList<>();
        for (int index : POSTS_WITH_MEDIA) {
            if (index < 0 || index >= roots.size()) {
                continue;
            }
            Post post = roots.get(index);
            MediaAsset asset = new MediaAsset();
            asset.setOwner(post.getUser());
            asset.setType(MediaType.IMAGE);
            asset.setPurpose(MediaPurpose.POST);
            asset.setUrl("https://picsum.photos/seed/aloute-posts-" + index + "/900/600");
            asset.setStoragePath("seed/posts/" + index + ".jpg");
            asset.setMimeType("image/jpeg");
            asset.setFileSize(240_000L);
            asset.setWidth(900);
            asset.setHeight(600);
            asset.setAttachedAt(ctx.now.minus(1, ChronoUnit.HOURS));
            assets.add(asset);
            targetPosts.add(post);
        }
        List<MediaAsset> savedAssets = mediaAssetRepository.saveAll(assets);
        List<PostMedia> links = new ArrayList<>();
        for (int i = 0; i < savedAssets.size(); i++) {
            links.add(new PostMedia(targetPosts.get(i), savedAssets.get(i), 0));
        }
        if (!links.isEmpty()) {
            postMediaRepository.saveAll(links);
        }
    }

    private void seedReplies(SeedContext ctx) {
        List<Post> replies = new ArrayList<>();
        List<Post> parents = new ArrayList<>();
        List<Instant> times = new ArrayList<>();
        for (int i = 0; i < ctx.roots.size(); i++) {
            Post parent = ctx.roots.get(i);
            int replyCount = i < 40 ? 2 : 1;
            Instant parentTime = ctx.postTimes.get(parent.getId());
            for (int r = 0; r < replyCount; r++) {
                User author = pickReplyAuthor(ctx, parent, i * 17 + r * 7 + 3);
                Post reply = new Post();
                reply.setUser(author);
                reply.setContent(DevSeedContent.REPLY_POOL[(i + r * 3) % DevSeedContent.REPLY_POOL.length]);
                reply.setParentPost(parent);
                reply.setCommunity(parent.getCommunity());
                reply.setVisibility(PostVisibility.PUBLIC);
                reply.setViewCount(5 + ((i * 13 + r * 7) % 80));
                Instant time = parentTime.plusSeconds((2 + ((i * 13 + r * 5) % 20)) * 3600L);
                Instant latestAllowed = ctx.now.minusSeconds(300);
                if (time.isAfter(latestAllowed)) {
                    time = latestAllowed.isAfter(parentTime) ? latestAllowed : parentTime.plusSeconds(60);
                }
                replies.add(reply);
                parents.add(parent);
                times.add(time);
            }
        }
        List<Post> saved = postRepository.saveAll(replies);
        ctx.replies.addAll(saved);
        for (int i = 0; i < saved.size(); i++) {
            Post reply = saved.get(i);
            ctx.postTimes.put(reply.getId(), times.get(i));
            Post parent = parents.get(i);
            ctx.replyCounts.merge(parent.getId(), 1L, Long::sum);
            addNotification(ctx, parent.getUser(), reply.getUser(), "POST_REPLY", "POST",
                    reply.getId(), reply.getContent(), times.get(i).plusSeconds(120));
        }
    }

    private void seedQuotes(SeedContext ctx) {
        List<Post> quotes = new ArrayList<>();
        List<Post> quotedPosts = new ArrayList<>();
        List<Instant> times = new ArrayList<>();
        for (DevSeedContent.DemoQuote definition : DevSeedContent.QUOTES) {
            if (definition.quotedIndex() < 0 || definition.quotedIndex() >= ctx.roots.size()) {
                continue;
            }
            Post quoted = ctx.roots.get(definition.quotedIndex());
            User author = ctx.users.get(definition.authorUsername());
            if (author == null) {
                continue;
            }
            Post quote = new Post();
            quote.setUser(author);
            quote.setContent(definition.content());
            quote.setQuotedPost(quoted);
            quote.setVisibility(PostVisibility.PUBLIC);
            quote.setAllowReply(true);
            quote.setAllowQuote(true);
            quote.setViewCount(20 + (long) definition.quotedIndex() * 7);
            Instant time = ctx.postTimes.get(quoted.getId()).plusSeconds(2 + (definition.quotedIndex() % 12) * 3600L);
            Instant latestAllowed = ctx.now.minusSeconds(300);
            if (time.isAfter(latestAllowed)) {
                Instant quotedTime = ctx.postTimes.get(quoted.getId());
                time = latestAllowed.isAfter(quotedTime) ? latestAllowed : quotedTime.plusSeconds(60);
            }
            quotes.add(quote);
            quotedPosts.add(quoted);
            times.add(time);
        }
        List<Post> saved = postRepository.saveAll(quotes);
        for (int i = 0; i < saved.size(); i++) {
            Post quote = saved.get(i);
            Post quoted = quotedPosts.get(i);
            ctx.roots.add(quote);
            ctx.postTimes.put(quote.getId(), times.get(i));
            ctx.quoteCounts.merge(quoted.getId(), 1L, Long::sum);
            addNotification(ctx, quoted.getUser(), quote.getUser(), "POST_QUOTE", "POST",
                    quote.getId(), quote.getContent(), times.get(i).plusSeconds(120));
        }
    }

    private void seedPolls(SeedContext ctx) {
        List<PollDefinition> definitions = List.of(
                new PollDefinition(10, "Bạn thường học nhóm ở đâu?", false,
                        new String[]{"Thư viện", "Canteen", "Quán cà phê", "Học online"}, true),
                new PollDefinition(32, "Bạn muốn tham gia môn thể thao nào nhất?", false,
                        new String[]{"Bóng đá", "Bóng rổ", "Kéo co", "Chạy bộ"}, false),
                new PollDefinition(34, "Buổi workshop AI nên diễn ra vào khung giờ nào?", false,
                        new String[]{"17h", "18h", "19h", "20h"}, false),
                new PollDefinition(54, "Bạn quan tâm loại học bổng nào nhất?", false,
                        new String[]{"Học bổng khuyến khích học tập", "Học bổng doanh nghiệp",
                                "Học bổng trao đổi quốc tế", "Học bổng sinh viên khó khăn"}, false),
                new PollDefinition(64, "Bạn muốn CLB biểu diễn thể loại nào trong đêm nhạc?", true,
                        new String[]{"Acoustic", "Pop", "Rock", "Ballad"}, false),
                new PollDefinition(97, "Bạn thích hình thức luyện tiếng Anh nào?", false,
                        new String[]{"Nói 1-1", "Nhóm nhỏ", "Thuyết trình", "Viết luận"}, false)
        );

        for (int p = 0; p < definitions.size(); p++) {
            PollDefinition definition = definitions.get(p);
            if (definition.postIndex() >= ctx.roots.size()) {
                continue;
            }
            Post post = ctx.roots.get(definition.postIndex());
            Instant postTime = ctx.postTimes.get(post.getId());
            Instant closedAt;
            if (definition.closed()) {
                closedAt = postTime.plusSeconds(3600);
                if (closedAt.isAfter(ctx.now)) {
                    closedAt = ctx.now.minusSeconds(60);
                }
            } else {
                closedAt = ctx.now.plus(4L + p, ChronoUnit.DAYS);
            }
            Poll poll = Poll.builder()
                    .post(post)
                    .question(definition.question())
                    .allowMultiple(definition.multiple())
                    .closedAt(closedAt)
                    .build();
            poll = pollRepository.save(poll);
            ctx.pollTimes.put(poll.getId(), postTime);

            List<PollOption> options = new ArrayList<>();
            for (int o = 0; o < definition.options().length; o++) {
                options.add(PollOption.builder()
                        .poll(poll)
                        .optionText(definition.options()[o])
                        .position(o)
                        .voteCount(0)
                        .build());
            }
            options = pollOptionRepository.saveAll(options);

            List<User> voters = new ArrayList<>(ctx.allUsers);
            if (p != 0) {
                voters.removeIf(user -> "user".equals(user.getUsername()));
            }
            Collections.shuffle(voters, new Random(200L + p));
            int voterCount = Math.min(voters.size(), 7 + (p * 5) % 13);
            int[] counts = new int[options.size()];
            List<PollVote> votes = new ArrayList<>();
            for (int v = 0; v < voterCount; v++) {
                User voter = voters.get(v);
                int firstPick = (v + p) % options.size();
                Set<Integer> picks = new LinkedHashSet<>();
                picks.add(firstPick);
                if (definition.multiple()) {
                    picks.add((firstPick + 1 + v % 2) % options.size());
                }
                for (int optionIndex : picks) {
                    votes.add(PollVote.builder()
                            .poll(poll)
                            .option(options.get(optionIndex))
                            .user(voter)
                            .build());
                    counts[optionIndex]++;
                }
            }
            pollVoteRepository.saveAll(votes);
            for (int o = 0; o < options.size(); o++) {
                options.get(o).setVoteCount(counts[o]);
            }
            pollOptionRepository.saveAll(options);
        }
    }

    private void seedEngagement(SeedContext ctx) {
        List<PostLike> likes = new ArrayList<>();
        List<Bookmark> bookmarks = new ArrayList<>();
        List<Repost> reposts = new ArrayList<>();
        for (int i = 0; i < ctx.roots.size(); i++) {
            Post post = ctx.roots.get(i);
            List<User> candidates = engagementCandidates(ctx, post);
            Collections.shuffle(candidates, new Random(9000L + i));
            Random random = new Random(5000L + i * 31L);

            int likeTarget = Math.min(candidates.size(), 3 + random.nextInt(11));
            List<PostLike> postLikes = new ArrayList<>();
            for (int k = 0; k < likeTarget; k++) {
                postLikes.add(new PostLike(candidates.get(k), post));
            }
            likes.addAll(postLikes);
            post.setLikeCount(postLikes.size());

            int bookmarkTarget = Math.min(candidates.size(), random.nextInt(4));
            for (int k = 0; k < bookmarkTarget; k++) {
                bookmarks.add(new Bookmark(candidates.get(k), post));
            }

            int repostTarget = Math.min(candidates.size(), random.nextInt(3));
            List<Repost> postReposts = new ArrayList<>();
            for (int k = 0; k < repostTarget; k++) {
                postReposts.add(new Repost(candidates.get(k), post));
            }
            reposts.addAll(postReposts);
            post.setRepostCount(postReposts.size());

            Instant postTime = ctx.postTimes.get(post.getId());
            if (i % 3 == 0 && !postLikes.isEmpty()) {
                addNotification(ctx, post.getUser(), postLikes.get(0).getUser(), "POST_LIKE", "POST",
                        post.getId(), post.getContent(), postTime.plusSeconds(3600));
            }
            if (i % 4 == 0 && !postReposts.isEmpty()) {
                addNotification(ctx, post.getUser(), postReposts.get(0).getUser(), "POST_REPOST", "POST",
                        post.getId(), post.getContent(), postTime.plusSeconds(5400));
            }
            post.setViewCount(post.getViewCount() + post.getLikeCount() * 9 + ctx.replyCounts.getOrDefault(post.getId(), 0L) * 4);
        }

        int replyIndex = 0;
        for (Post reply : ctx.replies) {
            List<User> candidates = engagementCandidates(ctx, reply);
            Collections.shuffle(candidates, new Random(13000L + replyIndex));
            int likeTarget = Math.min(candidates.size(), replyIndex % 4);
            List<PostLike> replyLikes = new ArrayList<>();
            for (int k = 0; k < likeTarget; k++) {
                replyLikes.add(new PostLike(candidates.get(k), reply));
            }
            likes.addAll(replyLikes);
            reply.setLikeCount(replyLikes.size());
            if (replyIndex % 5 == 0 && !replyLikes.isEmpty()) {
                addNotification(ctx, reply.getUser(), replyLikes.get(0).getUser(), "POST_LIKE", "POST",
                        reply.getId(), reply.getContent(), ctx.postTimes.get(reply.getId()).plusSeconds(900));
            }
            replyIndex++;
        }

        postLikeRepository.saveAll(likes);
        bookmarkRepository.saveAll(bookmarks);
        repostRepository.saveAll(reposts);
    }

    private void seedFollows(SeedContext ctx) {
        List<User> users = ctx.allUsers;
        List<Follow> follows = new ArrayList<>();
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < users.size(); i++) {
            int count = 6 + (i % 5);
            for (int k = 0; k < count; k++) {
                User follower = users.get(i);
                User following = users.get((i + 1 + k) % users.size());
                if (follower.getId().equals(following.getId()) || following.isPrivateAccount()) {
                    continue;
                }
                String edge = follower.getId() + "->" + following.getId();
                if (!edges.add(edge)) {
                    continue;
                }
                Follow follow = new Follow();
                follow.setId(new FollowId(follower.getId(), following.getId()));
                follow.setFollower(follower);
                follow.setFollowing(following);
                follows.add(follow);
                ctx.followingCounts.merge(follower.getId(), 1L, Long::sum);
                ctx.followerCounts.merge(following.getId(), 1L, Long::sum);

                if ((i * 11 + k) % 4 == 0) {
                    Instant time = ctx.now.minusSeconds(3600L * (8 + (i * 31 + k * 17) % 300));
                    addNotification(ctx, following, follower, "FOLLOW", "USER",
                            follower.getId(), follower.getUsername() + " đã bắt đầu theo dõi bạn", time);
                }
            }
        }
        addExplicitFollow(ctx, follows, edges, "khoinguyen", "user");
        followRepository.saveAll(follows);
    }

    private void addExplicitFollow(SeedContext ctx, List<Follow> follows, Set<String> edges,
                                   String followerName, String followingName) {
        User follower = ctx.users.get(followerName);
        User following = ctx.users.get(followingName);
        if (follower == null || following == null) {
            return;
        }
        String edge = follower.getId() + "->" + following.getId();
        if (!edges.add(edge)) {
            return;
        }
        Follow follow = new Follow();
        follow.setId(new FollowId(follower.getId(), following.getId()));
        follow.setFollower(follower);
        follow.setFollowing(following);
        follows.add(follow);
        ctx.followingCounts.merge(follower.getId(), 1L, Long::sum);
        ctx.followerCounts.merge(following.getId(), 1L, Long::sum);
    }

    private void seedConversations(SeedContext ctx) {
        List<ConversationSpec> specs = List.of(
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_FRIEND,
                        "user", "demoan"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_FRIEND,
                        "user", "khoinguyen"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_WORK,
                        "admin", "khoinguyen"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_FRIEND,
                        "user", "thuytien"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_STUDY,
                        "khoinguyen", "quangminh"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_FRIEND,
                        "user", "baongoc"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_WORK,
                        "admin", "demoan"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_WORK,
                        "user", "huunhan"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_FRIEND,
                        "quangminh", "baongoc"),
                new ConversationSpec(ConversationType.DIRECT, null, null, MESSAGES_EVENT,
                        "thuytien", "phuonganh"),
                new ConversationSpec(ConversationType.GROUP, "Nhóm đồ án AloUTE",
                        "https://picsum.photos/seed/aloute-group-do-an/200/200", MESSAGES_STUDY,
                        "user", "khoinguyen", "quangminh", "thanhdat"),
                new ConversationSpec(ConversationType.GROUP, "Hội đồng hướng dẫn đồ án",
                        "https://picsum.photos/seed/aloute-group-hoi-dong/200/200", MESSAGES_STUDY,
                        "admin", "user", "tuananh", "thayvan"),
                new ConversationSpec(ConversationType.GROUP, "CLB Tiếng Anh - Ban truyền thông",
                        "https://picsum.photos/seed/aloute-group-tieng-anh/200/200", MESSAGES_EVENT,
                        "user", "thuytien", "linhchi", "phuonganh"),
                new ConversationSpec(ConversationType.GROUP, "Ban tổ chức đêm nhạc UTE",
                        "https://picsum.photos/seed/aloute-group-dem-nhac/200/200", MESSAGES_EVENT,
                        "clb_guitar", "thienthan", "phuonganh", "thuytien", "user"),
                new ConversationSpec(ConversationType.GROUP, "CLB Lập trình - Team kỹ thuật",
                        "https://picsum.photos/seed/aloute-group-lap-trinh/200/200", MESSAGES_STUDY,
                        "khoinguyen", "thanhdat", "minhtri", "ducthanh", "quocbao"),
                new ConversationSpec(ConversationType.GROUP, "Nhóm thể thao cuối tuần",
                        "https://picsum.photos/seed/aloute-group-the-thao/200/200", MESSAGES_FRIEND,
                        "user", "hoangnam", "longvu", "baongoc")
        );

        for (int ci = 0; ci < specs.size(); ci++) {
            ConversationSpec spec = specs.get(ci);
            List<User> members = new ArrayList<>();
            for (String username : spec.members()) {
                User user = ctx.users.get(username);
                if (user != null) {
                    members.add(user);
                }
            }
            if (members.size() < 2) {
                log.warn("Skipping conversation {} because it has fewer than two known members", spec.name());
                continue;
            }

            Conversation conversation = new Conversation();
            conversation.setType(spec.type());
            conversation.setName(spec.name());
            conversation.setAvatarUrl(spec.avatarUrl());
            conversation = conversationRepository.save(conversation);

            Instant start = ctx.now.minus(12L - (ci % 8), ChronoUnit.DAYS)
                    .plusSeconds(ci * 137L);
            List<Message> messages = new ArrayList<>();
            List<Instant> messageTimes = new ArrayList<>();
            int messageCount = 8;
            for (int m = 0; m < messageCount; m++) {
                User sender = members.get((ci + m) % members.size());
                Message message = Message.builder()
                        .conversation(conversation)
                        .sender(sender)
                        .content(spec.pool()[m % spec.pool().length])
                        .build();
                messages.add(message);
                messageTimes.add(start.plusSeconds(m * 900L + (ci * 37L + m * 13L) % 240L));
            }
            messages = messageRepository.saveAll(messages);
            for (int m = 0; m < messages.size(); m++) {
                ctx.messageTimes.put(messages.get(m).getId(), messageTimes.get(m));
            }

            Long lastMessageId = messages.get(messages.size() - 1).getId();
            Instant lastMessageTime = messageTimes.get(messageTimes.size() - 1);
            List<ConversationMember> conversationMembers = new ArrayList<>();
            for (User user : members) {
                ConversationMember member = ConversationMember.builder()
                        .conversation(conversation)
                        .user(user)
                        .build();
                boolean leaveUnread = "user".equals(user.getUsername()) && (ci == 0 || ci == 10);
                if (leaveUnread && messages.size() > 4) {
                    member.setLastReadMessageId(messages.get(3).getId());
                    member.setLastReadAt(messageTimes.get(3));
                } else {
                    member.setLastReadMessageId(lastMessageId);
                    member.setLastReadAt(lastMessageTime);
                }
                conversationMembers.add(member);
            }
            conversationMemberRepository.saveAll(conversationMembers);
            ctx.conversationStartTimes.put(conversation.getId(), start);
            ctx.conversationLastTimes.put(conversation.getId(), lastMessageTime);
            ctx.conversationCount++;
        }
    }

    private void seedReportsAndModeration(SeedContext ctx) {
        List<Report> reports = new ArrayList<>();
        List<Instant> reportCreatedTimes = new ArrayList<>();
        User admin = ctx.users.get("admin");
        for (int i = 0; i < DevSeedContent.REPORTS.length; i++) {
            DevSeedContent.DemoReport definition = DevSeedContent.REPORTS[i];
            User reporter = ctx.users.get(definition.reporterUsername());
            if (reporter == null) {
                continue;
            }
            Report report = new Report();
            report.setReporter(reporter);
            report.setReportedUser(definition.reportedUsername() == null
                    ? null : ctx.users.get(definition.reportedUsername()));
            if (definition.reportedPostIndex() >= 0 && definition.reportedPostIndex() < ctx.roots.size()) {
                report.setReportedPostId(ctx.roots.get(definition.reportedPostIndex()).getId());
            }
            report.setReason(definition.reason());
            report.setDescription(definition.description());
            report.setStatus(definition.status());
            report.setResolutionNote(definition.resolutionNote());

            Instant createdAt = ctx.now.minus(1L + i, ChronoUnit.DAYS)
                    .minusSeconds((i * 211L) % 7200L);
            if (definition.status() != ReportStatus.PENDING) {
                report.setReviewedBy(admin);
                report.setReviewedAt(createdAt.plusSeconds(6 * 3600L));
                if (definition.status() == ReportStatus.RESOLVED
                        || definition.status() == ReportStatus.REJECTED) {
                    report.setClosedAt(createdAt.plusSeconds(12 * 3600L));
                }
            }
            reports.add(report);
            reportCreatedTimes.add(createdAt);
        }
        reports = reportRepository.saveAll(reports);
        for (int i = 0; i < reports.size(); i++) {
            ctx.reportTimes.put(reports.get(i).getId(), reportCreatedTimes.get(i));
        }

        List<ModerationAction> actions = new ArrayList<>();
        List<Instant> actionTimes = new ArrayList<>();
        for (Report report : reports) {
            if (report.getStatus() == ReportStatus.PENDING || report.getStatus() == ReportStatus.REVIEWED) {
                continue;
            }
            boolean postTarget = report.getReportedPostId() != null;
            boolean resolved = report.getStatus() == ReportStatus.RESOLVED;
            ModerationAction action = new ModerationAction();
            action.setActor(admin);
            action.setTargetType(postTarget ? ModerationTargetType.POST
                    : report.getReportedUser() != null ? ModerationTargetType.USER : ModerationTargetType.REPORT);
            action.setTargetId(postTarget ? report.getReportedPostId()
                    : report.getReportedUser() != null ? report.getReportedUser().getId() : report.getId());
            action.setActionType(resolved ? (postTarget ? "REMOVE_POST" : "WARN_USER") : "DISMISS_REPORT");
            action.setReasonCode(report.getReason());
            action.setReasonDetail(truncate(report.getResolutionNote() != null
                    ? report.getResolutionNote() : report.getDescription(), 300));
            action.setRelatedReportId(report.getId());
            actions.add(action);
            actionTimes.add(ctx.reportTimes.get(report.getId()).plusSeconds(7 * 3600L));
        }
        List<ModerationAction> savedActions = moderationActionRepository.saveAll(actions);
        for (int i = 0; i < savedActions.size(); i++) {
            ctx.moderationTimes.put(savedActions.get(i).getId(), actionTimes.get(i));
        }
    }

    private void seedNotificationSettings(SeedContext ctx) {
        List<UserNotificationSetting> settings = new ArrayList<>();
        for (int i = 0; i < ctx.allUsers.size(); i++) {
            User user = ctx.allUsers.get(i);
            settings.add(UserNotificationSetting.builder()
                    .user(user)
                    .likesEnabled(true)
                    .commentsEnabled(true)
                    .followsEnabled(true)
                    .mentionsEnabled(true)
                    .emailNotificationsEnabled(i % 3 != 0)
                    .soundEnabled(i % 4 != 0)
                    .build());
        }
        notificationSettingRepository.saveAll(settings);
    }

    private void seedUserTopics(SeedContext ctx) {
        List<Topic> topics = new ArrayList<>(ctx.topics.values());
        List<UserTopic> subscriptions = new ArrayList<>();
        for (int i = 0; i < ctx.allUsers.size(); i++) {
            User user = ctx.allUsers.get(i);
            int count = 3 + i % 4;
            Set<Long> chosen = new HashSet<>();
            for (int k = 0; k < count; k++) {
                Topic topic = topics.get((i * 2 + k * 3) % topics.size());
                if (chosen.add(topic.getId())) {
                    subscriptions.add(new UserTopic(user, topic));
                }
            }
        }
        userTopicRepository.saveAll(subscriptions);
    }

    private void seedNotifications(SeedContext ctx) {
        // A few explicit mention notifications so the MENTION tab is never empty.
        addNotification(ctx, ctx.users.get("thanhdat"), ctx.users.get("khoinguyen"), "POST_MENTION", "POST",
                ctx.roots.get(34).getId(), "Nhắc đến bạn trong bài viết về workshop AI.", ctx.now.minus(3, ChronoUnit.HOURS));
        addNotification(ctx, ctx.users.get("thuytien"), ctx.users.get("linhchi"), "POST_MENTION", "POST",
                ctx.roots.get(47).getId(), "Nhắc đến bạn trong bài viết về câu lạc bộ tiếng Anh.", ctx.now.minus(7, ChronoUnit.HOURS));
        addNotification(ctx, ctx.users.get("admin"), ctx.users.get("thayvan"), "POST_MENTION", "POST",
                ctx.roots.get(5).getId(), "Nhắc đến bạn trong thông báo lịch bảo vệ đồ án.", ctx.now.minus(1, ChronoUnit.DAYS));
        addNotification(ctx, ctx.users.get("user"), ctx.users.get("khoinguyen"), "REPLY_MENTION", "POST",
                ctx.replies.get(0).getId(), "Nhắc đến bạn trong một bình luận.", ctx.now.minus(5, ChronoUnit.HOURS));
        addNotification(ctx, ctx.users.get("phuonganh"), ctx.users.get("thuytien"), "POST_MENTION", "POST",
                ctx.roots.get(52).getId(), "Nhắc đến bạn trong bài viết về cuộc thi hùng biện.", ctx.now.minus(9, ChronoUnit.HOURS));

        List<Notification> notifications = new ArrayList<>();
        for (PendingNotification pending : ctx.pendingNotifications) {
            boolean read = pending.createdAt().isBefore(ctx.now.minus(3, ChronoUnit.DAYS));
            notifications.add(Notification.builder()
                    .recipient(pending.recipient())
                    .actor(pending.actor())
                    .type(pending.type())
                    .targetType(pending.targetType())
                    .targetId(pending.targetId())
                    .contentPreview(truncate(pending.preview(), 200))
                    .isRead(read)
                    .build());
        }
        List<Notification> saved = notificationRepository.saveAll(notifications);
        for (int i = 0; i < saved.size(); i++) {
            ctx.notificationTimes.put(saved.get(i).getId(), ctx.pendingNotifications.get(i).createdAt());
        }
    }

    private void finalizeCounters(SeedContext ctx) {
        for (Post post : ctx.roots) {
            post.setReplyCount(ctx.replyCounts.getOrDefault(post.getId(), 0L));
            post.setQuoteCount(ctx.quoteCounts.getOrDefault(post.getId(), 0L));
            if (post.getViewCount() <= 0) {
                post.setViewCount(30 + post.getLikeCount() * 7);
            }
        }
        postRepository.saveAll(ctx.roots);
        if (!ctx.replies.isEmpty()) {
            postRepository.saveAll(ctx.replies);
        }

        Map<Long, Long> postsByAuthor = new HashMap<>();
        Map<Long, Long> postsByCommunity = new HashMap<>();
        for (Post post : ctx.roots) {
            postsByAuthor.merge(post.getUser().getId(), 1L, Long::sum);
            if (post.getCommunity() != null) {
                postsByCommunity.merge(post.getCommunity().getId(), 1L, Long::sum);
            }
        }
        for (User user : ctx.allUsers) {
            Profile profile = profileRepository.findById(user.getId()).orElse(null);
            if (profile == null) {
                continue;
            }
            profile.setPostsCount(postsByAuthor.getOrDefault(user.getId(), 0L));
            profile.setFollowersCount(ctx.followerCounts.getOrDefault(user.getId(), 0L));
            profile.setFollowingCount(ctx.followingCounts.getOrDefault(user.getId(), 0L));
            profileRepository.save(profile);
        }
        for (Community community : ctx.communities.values()) {
            community.setPostCount(postsByCommunity.getOrDefault(community.getId(), 0L));
            community.setMemberCount((long) ctx.communityMembers.getOrDefault(community.getId(), Set.of()).size());
            communityRepository.save(community);
        }
    }

    private void backdateTimestamps(SeedContext ctx) {
        List<Object[]> users = new ArrayList<>();
        for (int i = 0; i < ctx.allUsers.size(); i++) {
            Instant created = ctx.now.minus(120L - i * 3L, ChronoUnit.DAYS);
            Timestamp timestamp = Timestamp.from(created);
            users.add(new Object[]{timestamp, timestamp, ctx.allUsers.get(i).getId()});
        }
        jdbcTemplate.batchUpdate("UPDATE users SET created_at = ?, updated_at = ? WHERE id = ?", users);

        List<Object[]> communities = new ArrayList<>();
        int index = 0;
        for (Community community : ctx.communities.values()) {
            Instant created = ctx.now.minus(45L - index * 4L, ChronoUnit.DAYS);
            Timestamp timestamp = Timestamp.from(created);
            communities.add(new Object[]{timestamp, timestamp, community.getId()});
            index++;
        }
        jdbcTemplate.batchUpdate("UPDATE communities SET created_at = ?, updated_at = ? WHERE id = ?", communities);

        List<Object[]> posts = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.postTimes.entrySet()) {
            Timestamp timestamp = Timestamp.from(entry.getValue());
            posts.add(new Object[]{timestamp, timestamp, entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?", posts);

        List<Object[]> conversations = new ArrayList<>();
        for (Long conversationId : ctx.conversationStartTimes.keySet()) {
            Timestamp created = Timestamp.from(ctx.conversationStartTimes.get(conversationId));
            Timestamp updated = Timestamp.from(ctx.conversationLastTimes.get(conversationId));
            conversations.add(new Object[]{created, updated, conversationId});
        }
        jdbcTemplate.batchUpdate("UPDATE conversations SET created_at = ?, updated_at = ? WHERE id = ?", conversations);

        List<Object[]> messages = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.messageTimes.entrySet()) {
            messages.add(new Object[]{Timestamp.from(entry.getValue()), entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE messages SET created_at = ? WHERE id = ?", messages);

        List<Object[]> notifications = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.notificationTimes.entrySet()) {
            notifications.add(new Object[]{Timestamp.from(entry.getValue()), entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE notifications SET created_at = ? WHERE id = ?", notifications);

        List<Object[]> polls = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.pollTimes.entrySet()) {
            polls.add(new Object[]{Timestamp.from(entry.getValue()), entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE polls SET created_at = ? WHERE id = ?", polls);

        List<Object[]> reports = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.reportTimes.entrySet()) {
            reports.add(new Object[]{Timestamp.from(entry.getValue()), entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE reports SET created_at = ? WHERE id = ?", reports);

        List<Object[]> actions = new ArrayList<>();
        for (Map.Entry<Long, Instant> entry : ctx.moderationTimes.entrySet()) {
            actions.add(new Object[]{Timestamp.from(entry.getValue()), entry.getKey()});
        }
        jdbcTemplate.batchUpdate("UPDATE moderation_actions SET created_at = ? WHERE id = ?", actions);
    }

    private List<Topic> topicsFor(SeedContext ctx, DevSeedContent.DemoPost post) {
        String content = post.content();
        String lower = content.toLowerCase(Locale.ROOT);
        Set<String> slugs = new LinkedHashSet<>();
        switch (post.communitySlug()) {
            case "fit-hcmute" -> {
                slugs.add("hoc-tap");
                slugs.add("lap-trinh");
            }
            case "sinh-vien-ute" -> {
                slugs.add("ute-connect");
                slugs.add("doi-song-sinh-vien");
            }
            case "clb-lap-trinh-ai" -> {
                slugs.add("lap-trinh");
                slugs.add("clb-khoa");
            }
            case "clb-tieng-anh-ute" -> {
                slugs.add("clb-khoa");
                slugs.add("hoc-tap");
            }
            case "hoc-bong-viec-lam" -> {
                slugs.add("thuc-tap");
                slugs.add("hoc-bong");
            }
            case "clb-am-nhac-ute" -> {
                slugs.add("am-nhac");
                slugs.add("clb-khoa");
            }
            case "khoi-nghiep-sinh-vien" -> {
                slugs.add("khoi-nghiep");
                slugs.add("clb-khoa");
            }
            case "nckh-ute" -> slugs.add("nghien-cuu");
            default -> slugs.add("doi-song-sinh-vien");
        }
        if (lower.contains("spring")) {
            slugs.add("spring-boot");
        }
        if (lower.contains("flutter")) {
            slugs.add("flutter");
        }
        if (content.contains("AI") || lower.contains("machine learning") || lower.contains("trí tuệ nhân tạo")
                || lower.contains("yolov8") || lower.contains("chatbot") || lower.contains("mô hình")) {
            slugs.add("ai-ml");
        }
        if (lower.contains("đồ án")) {
            slugs.add("do-an-tot-nghiep");
        }
        if (lower.contains("nghiên cứu") || lower.contains("paper") || lower.contains("thử nghiệm")) {
            slugs.add("nghien-cuu");
        }
        if (lower.contains("học bổng")) {
            slugs.add("hoc-bong");
        }
        if (lower.contains("thực tập") || lower.contains("việc làm") || lower.contains("tuyển")
                || lower.contains("phỏng vấn") || lower.contains("cv") || lower.contains("offer")) {
            slugs.add("thuc-tap");
        }
        if (lower.contains("nhạc") || lower.contains("guitar") || lower.contains("acoustic") || lower.contains("vocal")) {
            slugs.add("am-nhac");
        }
        if (lower.contains("bóng") || lower.contains("thể thao") || lower.contains("chạy") || lower.contains("kéo co")) {
            slugs.add("the-thao");
        }
        if (lower.contains("khởi nghiệp") || lower.contains("startup") || lower.contains("gọi vốn")) {
            slugs.add("khoi-nghiep");
        }

        List<Topic> topics = new ArrayList<>();
        for (String slug : slugs) {
            Topic topic = ctx.topics.get(slug);
            if (topic != null) {
                topics.add(topic);
            }
            if (topics.size() == 3) {
                break;
            }
        }
        if (topics.isEmpty()) {
            topics.add(ctx.topics.get("hoc-tap"));
        }
        return topics;
    }

    private User pickReplyAuthor(SeedContext ctx, Post parent, int seed) {
        List<User> candidates = communityAwareCandidates(ctx, parent);
        candidates.removeIf(user -> user.getId().equals(parent.getUser().getId()));
        if (candidates.isEmpty()) {
            return parent.getUser();
        }
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    private List<User> engagementCandidates(SeedContext ctx, Post post) {
        List<User> candidates = communityAwareCandidates(ctx, post);
        candidates.removeIf(user -> user.getId().equals(post.getUser().getId()));
        return candidates;
    }

    private List<User> communityAwareCandidates(SeedContext ctx, Post post) {
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            Set<Long> memberIds = ctx.communityMembers.getOrDefault(post.getCommunity().getId(), Set.of());
            List<User> members = new ArrayList<>();
            for (User user : ctx.allUsers) {
                if (memberIds.contains(user.getId())) {
                    members.add(user);
                }
            }
            return members;
        }
        return new ArrayList<>(ctx.allUsers);
    }

    private void addNotification(SeedContext ctx, User recipient, User actor, String type, String targetType,
                                 Long targetId, String preview, Instant at) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        Instant time = at.isAfter(ctx.now) ? ctx.now.minusSeconds(60) : at;
        ctx.pendingNotifications.add(new PendingNotification(
                recipient, actor, type, targetType, targetId, truncate(preview, 180), time));
    }

    private String displayNameFor(String username, DevSeedContent.DemoUser demo) {
        if (demo != null) {
            return demo.displayName();
        }
        return switch (username) {
            case "admin" -> "Quản Trị Viên AloUTE";
            case "user" -> "AloUTE User";
            case "demoan" -> "An Demo";
            case "demobinh" -> "Bình Demo";
            default -> username;
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private PostVisibility visibilityFor(int index) {
        return switch (index) {
            case 20, 82, 95, 97 -> PostVisibility.FOLLOWERS;
            default -> PostVisibility.PUBLIC;
        };
    }

    private record PollDefinition(int postIndex, String question, boolean multiple,
                                  String[] options, boolean closed) {
    }

    private record ConversationSpec(ConversationType type, String name, String avatarUrl,
                                    String[] pool, String... members) {
    }

    private record PendingNotification(User recipient, User actor, String type, String targetType,
                                       Long targetId, String preview, Instant createdAt) {
    }

    private static final class SeedContext {
        private final Instant now;
        private final Map<String, User> users = new LinkedHashMap<>();
        private final List<User> allUsers = new ArrayList<>();
        private final Map<String, Topic> topics = new LinkedHashMap<>();
        private final Map<String, Community> communities = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> communityMembers = new HashMap<>();
        private final List<Post> roots = new ArrayList<>();
        private final List<Post> replies = new ArrayList<>();
        private final Map<Long, Instant> postTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> messageTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> notificationTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> reportTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> moderationTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> pollTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> conversationStartTimes = new LinkedHashMap<>();
        private final Map<Long, Instant> conversationLastTimes = new LinkedHashMap<>();
        private final Map<Long, Long> replyCounts = new HashMap<>();
        private final Map<Long, Long> quoteCounts = new HashMap<>();
        private final Map<Long, Long> followerCounts = new HashMap<>();
        private final Map<Long, Long> followingCounts = new HashMap<>();
        private final List<PendingNotification> pendingNotifications = new ArrayList<>();
        private int conversationCount;

        private SeedContext(Instant now) {
            this.now = now;
        }
    }
}
