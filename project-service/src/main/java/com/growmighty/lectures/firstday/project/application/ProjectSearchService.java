package com.growmighty.lectures.firstday.project.application;

import com.growmighty.lectures.firstday.project.infrastructure.search.ProjectDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectSearchService {

    private final ElasticsearchOperations operations;

    // TODO(팀): 랭킹 시그널 재설계 — 이커머스의 salesCount(functionScore) 자리에
    //           후원자 수·달성률·마감 임박 등 펀딩 시그널을 넣을지 결정
    public List<ProjectDocument> search(String keyword, Double minGoalAmount, Double maxGoalAmount, int page, int size) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> {
                // ① 관련도(점수 계산 O): 프로젝트 제목 가중치 3배 + 오타 허용
                b.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("title^3", "description")
                    .fuzziness("AUTO")));
                // ② 필터(점수 계산 X, 캐시 O): 펀딩 진행중 + 목표 금액 범위
                b.filter(f -> f.term(t -> t.field("status").value("OPEN")));
                if (minGoalAmount != null || maxGoalAmount != null) {
                    b.filter(f -> f.range(r -> r.number(n -> {
                        n.field("goalAmount");
                        if (minGoalAmount != null) n.gte(minGoalAmount);
                        if (maxGoalAmount != null) n.lte(maxGoalAmount);
                        return n;
                    })));
                }
                return b;
            }))
            .withPageable(PageRequest.of(page, size))
            .build();

        SearchHits<ProjectDocument> hits = operations.search(query, ProjectDocument.class);
        return hits.getSearchHits().stream().map(h -> h.getContent()).toList();
    }

    public List<String> autocomplete(String prefix) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.match(m -> m.field("title.auto").query(prefix)))
            .withPageable(PageRequest.of(0, 10))
            .build();
        return operations.search(query, ProjectDocument.class)
            .getSearchHits().stream()
            .map(h -> h.getContent().getTitle())
            .distinct()
            .toList();
    }
}
