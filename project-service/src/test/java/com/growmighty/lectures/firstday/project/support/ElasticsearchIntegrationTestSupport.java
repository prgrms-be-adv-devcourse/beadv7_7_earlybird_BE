package com.growmighty.lectures.firstday.project.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
}
