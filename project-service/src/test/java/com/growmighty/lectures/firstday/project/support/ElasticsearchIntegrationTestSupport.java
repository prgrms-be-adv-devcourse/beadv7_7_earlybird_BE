package com.growmighty.lectures.firstday.project.support;

import com.growmighty.lectures.firstday.project.project.infrastructure.search.ProjectDocument;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Paths;

/**
 * MySqlIntegrationTestSupport와 같은 싱글턴 컨테이너 패턴. 순정 elasticsearch 이미지가 아니라
 * infrastructure/elasticsearch/Dockerfile로 빌드한 nori 포함 이미지를 그대로 써서, 로컬/CI
 * 어디서든 실제 nori 형태소분석기 동작까지 검증한다.
 *
 * <p>{@code ElasticsearchContainer}는 {@code String}/{@code DockerImageName} 생성자만 노출하고
 * {@code ImageFromDockerfile}(Future&lt;String&gt;)을 직접 받지 않는다 — 그래서 먼저 {@code get()}으로
 * 빌드를 완료시켜 이미지 이름을 얻은 뒤, 공식 이미지와 호환된다고 명시({@code asCompatibleSubstituteFor})해
 * ElasticsearchContainer의 내부 이미지 이름 검증을 통과시킨다.
 *
 * <p>{@code MySqlIntegrationTestSupport}를 상속한다 — 이 모듈의 {@code @SpringBootTest}는 항상
 * 전체 컨텍스트(spring-data-jpa 포함)를 띄우므로, ES만 검증하는 테스트라도 datasource가 없으면
 * 컨텍스트 로딩 자체가 실패한다. 그래서 MySQL 컨테이너도 함께 공급한다.
 *
 * <p>컨테이너와 {@code projects} 인덱스는 모든 검색 통합 테스트가 공유하는 싱글턴이다. 각 테스트가
 * {@code @AfterEach}에서 색인을 지우더라도 {@code adapter.remove()}는 이벤트 기반 비동기라 다음 테스트
 * 클래스가 시작될 때까지 잔여 문서가 남을 수 있다 — 그래서 여기서 매 테스트 시작 전 인덱스를
 * 동기적으로 비워 테스트 간 격리를 보장한다.
 */
public abstract class ElasticsearchIntegrationTestSupport extends MySqlIntegrationTestSupport {

    private static final DockerImageName NORI_ELASTICSEARCH_IMAGE = DockerImageName.parse(
            new ImageFromDockerfile("project-service-test-es", false)
                    .withDockerfile(Paths.get("../infrastructure/elasticsearch/Dockerfile"))
                    .get())
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");

    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(NORI_ELASTICSEARCH_IMAGE)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    static {
        ELASTICSEARCH.start();
    }

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    /** 공유 {@code projects} 인덱스를 매 테스트 시작 전 동기적으로 비워 테스트 간 문서 오염을 막는다. */
    @BeforeEach
    void clearSharedProjectIndex() {
        if (elasticsearchOperations == null) {
            return;
        }
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProjectDocument.class);
        if (indexOps.exists()) {
            elasticsearchOperations.delete(DeleteQuery.builder(Query.findAll()).build(), ProjectDocument.class);
            indexOps.refresh();
        }
    }
}
