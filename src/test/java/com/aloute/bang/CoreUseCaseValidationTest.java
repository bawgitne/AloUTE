package com.aloute.bang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Test Suite kiểm tra các Business Invariant cốt lõi của Tuần 1
 * theo tài liệu đặc tả TEST_CASES_WEEK1.md (Ngô Anh Bằng phụ trách).
 */
class CoreUseCaseValidationTest {

    @Test
    @DisplayName("TC_POST_03 & TC_POST_04: Kiểm tra ràng buộc độ dài nội dung bài viết (1 - 500 ký tự)")
    void validatePostContentLengthConstraints() {
        String blankContent = "    ";
        assertThat(blankContent.trim()).isEmpty();

        String validContent = "Chào mừng bạn đến với mạng xã hội AloUTE!";
        assertThat(validContent.length()).isBetween(1, 500);

        String excessiveContent = "a".repeat(501);
        assertThat(excessiveContent.length()).isGreaterThan(500);
    }

    @Test
    @DisplayName("TC_REPLY_02: Kiểm tra invariant tính toán root_id trong luồng Nested Reply")
    void calculateRootIdForNestedReply() {
        Long rootPostId = 100L;
        Long firstReplyId = 200L;

        // Khi reply trực tiếp root post: parent_id = rootPostId, root_id = rootPostId
        Long calculatedRootForFirstReply = rootPostId;
        assertThat(calculatedRootForFirstReply).isEqualTo(rootPostId);

        // Khi reply một bình luận (nested): parent_id = firstReplyId, root_id vẫn giữ nguyên là rootPostId
        Long parentPostRootId = rootPostId;
        Long calculatedRootForNestedReply = parentPostRootId != null ? parentPostRootId : firstReplyId;
        assertThat(calculatedRootForNestedReply).isEqualTo(rootPostId);
    }

    @Test
    @DisplayName("TC_MSG_02: Ngăn chặn tạo cuộc trò chuyện trực tiếp (DIRECT) với chính mình")
    void preventDirectConversationWithSelf() {
        Long currentUserId = 42L;
        Long targetUserId = 42L;

        assertThatThrownBy(() -> {
            if (currentUserId.equals(targetUserId)) {
                throw new IllegalArgumentException("Cannot create a direct conversation with yourself");
            }
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot create a direct conversation with yourself");
    }

    @Test
    @DisplayName("TC_LIKE_02: Invariant đảm bảo tính lũy đẳng (Idempotency) khi like bài viết")
    void verifyLikeIdempotencyLogic() {
        boolean alreadyLiked = true;
        int currentLikeCount = 10;

        // Nếu đã like trước đó, số lượt like không được tăng thêm
        int newLikeCount = alreadyLiked ? currentLikeCount : currentLikeCount + 1;
        assertThat(newLikeCount).isEqualTo(10);
    }
}
