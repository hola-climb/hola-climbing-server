package com.holaclimbing.server.infrastructure.gcs;

import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.holaclimbing.server.common.config.CacheConfig;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GCS v4 Signed URL 발급.
 * 영상 바이너리는 Spring을 거치지 않고 클라이언트가 GCS와 직접 주고받는다.
 *
 * <p>Storage 클라이언트와 서명자(ServiceAccountSigner)는 분리해서 받는다.
 * 테스트에선 Storage가 NoCredentials로 fake-gcs-server를 치고, 서명만 별도로 생성한 SA로 한다.
 * 운영에선 둘 다 ApplicationDefaultCredentials에서 유도된 같은 자격증명을 사용한다.</p>
 *
 * <p><b>재시도:</b> SA 키 로컬 서명은 네트워크가 없지만, 운영 Workload Identity 경로는 IAM
 * {@code signBlob} API를 호출(네트워크)하므로 일시적 실패가 가능하다. signUrl 호출을
 * 지수 백오프로 최대 3회 재시도한다. (단일 외부 호출이라 AOP 재시도 프레임워크 대신
 * 경량 수동 재시도 — Spring Boot 4 호환성 리스크 회피.)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GcsStorageService {

    /** google-cloud-storage가 기본으로 사용하는 GCS 엔드포인트. 이 값이 그대로면 운영 환경으로 본다. */
    private static final String DEFAULT_GCS_HOST = "https://storage.googleapis.com";
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    private final Storage storage;
    private final GcsProperties properties;
    private final ServiceAccountSigner signer;

    /**
     * 클라이언트가 PUT으로 직접 업로드할 Signed URL.
     * 업로드 PUT 요청은 여기서 서명에 포함된 contentType과 동일한 Content-Type 헤더를 보내야 한다.
     */
    public String createUploadUrl(String objectPath, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectPath))
                .setContentType(contentType)
                .build();
        List<Storage.SignUrlOption> opts = new ArrayList<>(List.of(
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withContentType(),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(signer)));
        applyCustomHostName(opts);
        return normalizeSchemeForCustomHost(signWithRetry(blobInfo, opts).toString());
    }

    /**
     * 영상 재생용 읽기 Signed URL. objectPath가 없으면 null.
     * 같은 objectPath는 5분간 캐시 — 피드 N건이 같은 영상을 가리킬 때 반복 서명 비용을 줄인다.
     * (캐시 TTL 5분 &lt; URL 유효 15분 이므로 만료 직전 URL이 캐시에서 나가지 않는다.)
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_GCS_READ_URL, key = "#objectPath",
            condition = "#objectPath != null && !#objectPath.isBlank()", unless = "#result == null")
    public String createReadUrl(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectPath)).build();
        List<Storage.SignUrlOption> opts = new ArrayList<>(List.of(
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(signer)));
        applyCustomHostName(opts);
        return normalizeSchemeForCustomHost(signWithRetry(blobInfo, opts).toString());
    }

    /** 서버가 직접 받은 작은 바이너리를 GCS 객체로 저장한다. */
    public void uploadBytes(String objectPath, String contentType, byte[] bytes) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectPath))
                .setContentType(contentType)
                .build();
        try {
            storage.create(blobInfo, bytes);
        } catch (RuntimeException e) {
            log.warn("GCS 객체 업로드 실패 — objectPath={}, reason={}", objectPath, e.getMessage());
            throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED);
        }
    }

    /**
     * signUrl을 지수 백오프로 최대 MAX_ATTEMPTS회 재시도. 모두 실패하면 GCS_UPLOAD_FAILED.
     * 로컬 SA 서명은 사실상 1회에 성공/실패가 확정되지만, IAM signBlob 경로의 일시적 5xx·타임아웃을 흡수한다.
     */
    private URL signWithRetry(BlobInfo blobInfo, List<Storage.SignUrlOption> opts) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return storage.signUrl(blobInfo, properties.signedUrlMinutes(), TimeUnit.MINUTES,
                        opts.toArray(new Storage.SignUrlOption[0]));
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("GCS signUrl 최종 실패 ({}회 시도) — {}", MAX_ATTEMPTS, e.getMessage());
                    throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED);
                }
                log.warn("GCS signUrl 실패 (시도 {}/{}) — {} — {}ms 후 재시도",
                        attempt, MAX_ATTEMPTS, e.getMessage(), backoffMs);
                sleep(backoffMs);
                backoffMs *= 2;
            }
        }
        throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED); // 도달 불가 (루프가 항상 반환/throw)
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED);
        }
    }

    /**
     * Storage 클라이언트가 기본 GCS 호스트가 아닌 다른 엔드포인트로 설정돼 있으면
     * (테스트의 fake-gcs-server 등) 그 호스트를 Signed URL에도 박아 넣는다.
     * 그래야 PUT/GET이 실제로 그 엔드포인트로 향한다.
     */
    private void applyCustomHostName(List<Storage.SignUrlOption> opts) {
        String host = normalizedStorageHost();
        if (host != null && !DEFAULT_GCS_HOST.equals(host)) {
            opts.add(Storage.SignUrlOption.withHostName(host));
        }
    }

    /**
     * google-cloud-storage SDK는 v4 Signed URL의 스킴을 항상 https://로 박아 넣는다.
     * 운영(기본 호스트)에선 그게 맞지만, 평문 http로 떠 있는 fake-gcs-server에 대해선
     * 발급된 URL을 그대로 쓰면 SSL 핸드셰이크에서 깨진다. host 스킴이 http면 동일하게 맞춰준다.
     */
    private String normalizeSchemeForCustomHost(String signedUrl) {
        String host = normalizedStorageHost();
        if (host != null && host.startsWith("http://") && signedUrl.startsWith("https://")) {
            return "http://" + signedUrl.substring("https://".length());
        }
        return signedUrl;
    }

    private String normalizedStorageHost() {
        String host = storage.getOptions().getHost();
        if (host == null) {
            return null;
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }
}
