package com.aloute.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-19: developer-only surfaces must not ship in the production artifact.
 * The /khoi-test console (template + JS with pre-filled seed credentials) was
 * removed; this guard keeps it out of the public whitelist.
 */
class SecurityWhitelistGuardTest {

    @Test
    @DisplayName("Public whitelist contains no developer console paths")
    void noDevConsolePathsInPublicWhitelist() {
        for (String entry : SecurityWhitelist.PUBLIC_GET) {
            assertThat(entry).as("Public GET whitelist entry").doesNotContain("khoi-test");
        }
        for (String entry : SecurityWhitelist.PUBLIC_WEB) {
            assertThat(entry).as("Public web whitelist entry").doesNotContain("khoi-test");
        }
    }
}
