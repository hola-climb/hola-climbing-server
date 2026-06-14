package com.holaclimbing.server.infrastructure.gcs;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GcsConfigTest {

    @Test
    @DisplayName("storage — STORAGE_EMULATOR_HOST가 있으면 fake GCS host를 사용한다")
    void storage_withEmulatorHost_usesCustomHost() {
        GcsConfig config = new GcsConfig();

        Storage storage = config.storage("http://127.0.0.1:4443");

        assertThat(storage.getOptions().getHost()).isEqualTo("http://127.0.0.1:4443");
    }
}
