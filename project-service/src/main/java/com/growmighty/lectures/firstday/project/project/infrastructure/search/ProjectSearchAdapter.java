package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * index()/remove()는 ES를 직접 부르지 않고 이벤트만 발행한다.
 * 사전 계산된 임베딩(Project.getEmbedding())을 사용하여 ES에 색인하며,
 * 검색 시 Nori 형태소 분석기 키워드 매칭과 kNN 벡터 하이브리드 검색을 함께 실행한다.
 *
 * <p>autocomplete()는 title에 대해 공백으로 나눈 각 단어를 개별 prefix 쿼리로 만들고 전부 AND로
 * 묶는다 — title은 nori로 분석되어 형태소 토큰으로 쪼개지므로, 검색어를 통째로 하나의 prefix로 물으면
 * 토큰과 정확히 일치하지 않는 한(공백 포함 검색어는 애초에 매치될 수 없음) 매치가 안 된다. 단어별로
 * "제목의 어떤 토큰이 이 단어로 시작하는가"를 각각 물어 AND로 묶으면, 순서/위치와 무관하게 제목 안
 * 어디에 있는 단어든 매치되면서(예: "밥"이 "고양이 밥 주는 기계"에 매치) 여러 단어를 넣을수록 결과가
 * 점점 좁혀지는(모든 단어가 각자 어떤 토큰이든 prefix 매치해야 함) 동작을 얻는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
    private static final int MAX_RESULTS = 200;
    /** ES 후보 과다조회 한도 — 최종 10개 컷은 ProjectServiceImpl이 MySQL 가시성 필터링 후 수행한다. */
    private static final int AUTOCOMPLETE_CANDIDATE_LIMIT = 50;

    private final ElasticsearchOperations elasticsearchOperations;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;

    @Override
    public void index(Project project) {
        eventPublisher.publishEvent(new ProjectIndexRequestedEvent(project.getProjectId()));
    }

    @Override
    public void remove(Long projectId) {
        eventPublisher.publishEvent(new ProjectRemovedFromIndexEvent(projectId));
    }

    /**
     * 실제 색인 실행 — ProjectSearchIndexEventListener가 트랜잭션 커밋 후 DB에서 프로젝트를 다시
     * 조회해 최신 상태를 넘겨준다. DB에 저장된 사전 생성 임베딩(project.getEmbedding())을 사용한다.
     */
    void applyIndex(Project project) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    float[] embedding = project.getEmbedding();
                    if (embedding != null && embedding.length == 0) {
                        embedding = null;
                    }
                    elasticsearchOperations.save(new ProjectDocument(
                            project.getProjectId(), project.getTitle(), project.getSummary(),
                            project.getDescription(), embedding));
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });
    }

    /** 실제 삭제 실행 — ProjectSearchIndexEventListener가 트랜잭션 커밋 후에만 호출한다. */
    void applyRemove(Long projectId) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    elasticsearchOperations.delete(String.valueOf(projectId), ProjectDocument.class);
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 삭제 실패. projectId={}", projectId, cause);
                    return null;
                });
    }

    @Override
    public List<Long> search(String keyword) {
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> doSearch(keyword),
                this::searchFallback);
    }

    @Override
    public List<ProjectSuggestion> autocomplete(String prefix) {
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_AUTOCOMPLETE_ID).run(
                () -> doAutocomplete(prefix),
                this::autocompleteFallback);
    }

    private List<Long> doSearch(String keyword) {
        float[] queryVector = embeddingService.generateEmbedding(keyword);
        Query query;
        if (queryVector != null && queryVector.length > 0) {
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) {
                vectorList.add(f);
            }
            query = Query.of(q -> q.bool(b -> b
                    .should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f)))
                    .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f)))
                    .should(s -> s.match(m -> m.field("description").query(keyword)))
                    .should(s -> s.knn(k -> k
                            .field("embedding")
                            .queryVector(vectorList)
                            .k(10)
                            .numCandidates(100)
                            .boost(10.0f)))));
        } else {
            query = Query.of(q -> q.bool(b -> b
                    .should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f)))
                    .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f)))
                    .should(s -> s.match(m -> m.field("description").query(keyword)))));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(MAX_RESULTS)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream().map(hit -> hit.getContent().projectId()).toList();
    }

    private List<Long> searchFallback(Throwable cause) {
        log.warn("프로젝트 검색 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

    /**
     * 공백으로 나눈 단어마다 title에 대한 prefix 쿼리를 만들고 전부 must(AND)로 묶는다 — 클래스
     * Javadoc 참고. 공백만 있는 검색어(빈 단어 목록)는 ES를 부르지 않고 바로 빈 결과를 반환한다.
     */
    private List<ProjectSuggestion> doAutocomplete(String prefix) {
        List<String> words = Arrays.stream(prefix.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        Query query = Query.of(q -> q.bool(b -> {
            words.forEach(word -> b.must(m -> m.prefix(p -> p.field("title").value(word).caseInsensitive(true))));
            return b;
        }));
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(AUTOCOMPLETE_CANDIDATE_LIMIT)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .map(hit -> new ProjectSuggestion(hit.getContent().projectId(), hit.getContent().title()))
                .toList();
    }

    private List<ProjectSuggestion> autocompleteFallback(Throwable cause) {
        log.warn("프로젝트 자동완성 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }
}
