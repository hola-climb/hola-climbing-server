package com.holaclimbing.server.infrastructure.gcs;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.holaclimbing.server.domain.video.VideoUploadProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * GCS 연동 설정. Storage 클라이언트는 GOOGLE_APPLICATION_CREDENTIALS 환경변수로 인증한다.
 * test 프로파일에서는 TestcontainersConfiguration이 대체 Storage·서명자 빈을 제공한다.
 */
@Configuration
@EnableConfigurationProperties({GcsProperties.class, VideoUploadProperties.class})
public class GcsConfig {

    @Bean
    @Profile("!test")
    public Storage storage(@Value("${STORAGE_EMULATOR_HOST:}") String emulatorHost) {
        if (StringUtils.hasText(emulatorHost)) {
            return StorageOptions.newBuilder()
                    .setHost(trimTrailingSlash(emulatorHost.trim()))
                    .setProjectId("hola-local")
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService();
        }
        return StorageOptions.getDefaultInstance().getService();
    }

    /**
     * Signed URL 서명자. 운영에서는 ApplicationDefaultCredentials이 ServiceAccountSigner를 구현한다
     * (서비스 계정 JSON 키로 인증한 경우).
     */
    @Bean
    @Profile("!test")
    public ServiceAccountSigner gcsSigner() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        if (credentials instanceof ServiceAccountSigner signer) {
            return signer;
        }
        throw new IllegalStateException(
                "GCS Signed URL 발급에는 ServiceAccountSigner-구현 자격증명이 필요합니다. " +
                        "GOOGLE_APPLICATION_CREDENTIALS가 서비스 계정 키 JSON을 가리키는지 확인하세요.");
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
