package com.holaclimbing.server;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.holaclimbing.server.infrastructure.gcs.GcsProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * 테스트 인프라:
 * <ul>
 *   <li>PostgreSQL — 실제 컨테이너로 SQL/JSONB/Index 동작까지 검증</li>
 *   <li>Redis — Streams 큐, Pub/Sub, 상태 저장소를 진짜로 사용</li>
 *   <li>fake-gcs-server — GCS API를 흉내내는 컨테이너. Signed URL 발급·PUT 업로드·GET 다운로드까지
 *       실제 SDK로 end-to-end 검증한다. (단, IAM·CORS·실서명 검증은 실제 GCS에서만 가능 — 수동 smoke test 영역)</li>
 * </ul>
 *
 * <p>Storage 클라이언트는 NoCredentials로 fake-gcs-server에 붙고, Signed URL 서명자는
 * 런타임 생성한 RSA 키쌍 기반의 더미 SA로 분리한다. fake-gcs-server는 서명을 검증하지 않으므로
 * 서명 무결성과 무관하게 PUT/GET이 동작한다.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
				.asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
	}

	/** GCS 에뮬레이터. 컨테이너 라이프사이클은 Spring이 관리 (initMethod=start). */
	@Bean(initMethod = "start", destroyMethod = "stop")
	GenericContainer<?> fakeGcsContainer() {
		return new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:1.50.2"))
				.withExposedPorts(4443)
				.withCommand("-scheme", "http", "-port", "4443");
	}

	/** fake-gcs-server를 가리키는 Storage 클라이언트. API 호출은 인증 없이. */
	@Bean
	Storage gcsStorage(GenericContainer<?> fakeGcsContainer, GcsProperties props) {
		String endpoint = "http://" + fakeGcsContainer.getHost() + ":" + fakeGcsContainer.getMappedPort(4443);
		Storage storage = StorageOptions.newBuilder()
				.setHost(endpoint)
				.setProjectId("hola-test")
				.setCredentials(NoCredentials.getInstance())
				.build()
				.getService();

		// 운영 버킷과 동일 이름으로 미리 생성 (이미 존재하면 무시)
		try {
			storage.create(BucketInfo.of(props.bucket()));
		} catch (StorageException ignored) {
		}
		return storage;
	}

	/**
	 * Signed URL 서명자. V4 서명에는 RSA 개인키가 필요하므로 런타임에 키쌍을 생성한다.
	 * fake-gcs-server는 서명을 검증하지 않으므로 어떤 키로 서명하든 통과한다.
	 */
	@Bean
	ServiceAccountSigner gcsSigner() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		return ServiceAccountCredentials.newBuilder()
				.setClientEmail("test@hola-test.iam.gserviceaccount.com")
				.setPrivateKey(pair.getPrivate())
				.setPrivateKeyId("test-key-id")
				.setProjectId("hola-test")
				.build();
	}

}
