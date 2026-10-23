package com.aloute.controller.api;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.MediaPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression guard for multipart {@code purpose} binding on POST /api/media.
 *
 * <p>The Settings page sends the purpose as a natural multipart text part ({@code USER_AVATAR}) while the
 * post composer sends it as a JSON string part ({@code "POST"}). A JSON string part may keep its surrounding
 * quotes when it is handed over as plain text, so both shapes must resolve to the same enum value.
 */
class MediaControllerPurposeBindingTest {

    @Test
    @DisplayName("Plain text purpose part binds (Settings page shape)")
    void plainTextPurposeBinds() {
        assertThat(MediaController.parsePurpose("USER_AVATAR")).isEqualTo(MediaPurpose.USER_AVATAR);
        assertThat(MediaController.parsePurpose("USER_COVER")).isEqualTo(MediaPurpose.USER_COVER);
    }

    @Test
    @DisplayName("JSON string purpose part binds (post composer shape, quotes may be preserved)")
    void jsonEncodedPurposeBinds() {
        assertThat(MediaController.parsePurpose("\"POST\"")).isEqualTo(MediaPurpose.POST);
        assertThat(MediaController.parsePurpose(" \"USER_AVATAR\" ")).isEqualTo(MediaPurpose.USER_AVATAR);
    }

    @Test
    @DisplayName("Case and surrounding whitespace are tolerated")
    void caseAndWhitespaceTolerated() {
        assertThat(MediaController.parsePurpose("  user_cover  ")).isEqualTo(MediaPurpose.USER_COVER);
    }

    @Test
    @DisplayName("Unknown purpose is rejected with 422 MEDIA_PURPOSE_INVALID")
    void unknownPurposeRejected() {
        assertThatThrownBy(() -> MediaController.parsePurpose("NOT_A_PURPOSE"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getCode()).isEqualTo("MEDIA_PURPOSE_INVALID");
                    assertThat(apiException.getStatus().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("Blank purpose is rejected with 422 MEDIA_PURPOSE_REQUIRED")
    void blankPurposeRejected() {
        assertThatThrownBy(() -> MediaController.parsePurpose("   "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getCode()).isEqualTo("MEDIA_PURPOSE_REQUIRED");
                    assertThat(apiException.getStatus().value()).isEqualTo(422);
                });
    }
}
