package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 OpenAI를 호출하지 않는다 — EmbeddingModel을 텍스트별 결정적 랜덤 벡터를 만드는 스텁으로
 * 교체해서 nori 매치와 kNN 둘 다 검증 가능하게 한다. 벡터의 딱 한 축(index 0)만 채우면 코사인
 * 유사도가 두 텍스트 hashCode의 부호가 같은지만으로 항상 정확히 +1/-1이 되어버려(다른 축이 전부
 * 0이라 크기 비로 상쇄됨), 서로 무관한 텍스트끼리도 우연히 +1로 "완전 유사" 판정을 받을 수 있다.
 * 그래서 1536차원 전체를 텍스트 hashCode로 시드한 난수로 채운다 — 같은 텍스트는 항상 같은
 * 벡터(결정적)를 얻고, 다른 텍스트는 고차원 랜덤 벡터의 성질상 코사인 유사도가 0 근방에 몰려
 * ProjectSearchAdapter의 kNN 유사도 하한(0.5)을 사실상 통과하지 못한다.
 *
 * <p>adapter.index()는 이제 실제 색인 시점(AFTER_COMMIT 리스너)에 ProjectRepository로 프로젝트를
 * 다시 조회한다(멱등성 — ProjectIndexRequestedEvent 주석 참고) — 그래서 이 테스트도 메모리에만
 * 있는 Project가 아니라 실제로 projectRepository에 저장한 프로젝트를 넘겨야 한다. 그러지 않으면
 * 리스너가 "조회했더니 없다"고 보고 색인을 건너뛴다.
 */
@SpringBootTest
class ProjectSearchAdapterIntegrationTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;

    private final List<Long> savedProjectIds = new ArrayList<>();

    private Project savedProject(String title) {
        Project project = Project.register(1L, null, title, 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(project);
        savedProjectIds.add(saved.getProjectId());
        return saved;
    }

    // ElasticsearchIntegrationTestSupport의 ES 컨테이너는 JVM당 싱글턴으로 여러 테스트 클래스가
    // 같은 "projects" 인덱스를 공유한다 — 여기서 색인한 문서를 안 지우면 다른 테스트 클래스의
    // 전체 인덱스 검색(예: ProjectSearchIndexBootstrapTest)에 우리 문서가 섞여 들어간다.
    @AfterEach
    void cleanUpIndexedDocuments() {
        savedProjectIds.forEach(adapter::remove);
    }

    @Test
    @DisplayName("색인한 프로젝트를 제목 키워드로 검색하면 찾아진다")
    void index_then_search_findsByKeyword() {
        Project matching = savedProject("한국어 형태소 분석 테스트 프로젝트");
        Project other = savedProject("완전히 다른 내용의 프로젝트");
        adapter.index(matching);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("분석");
            assertThat(result).contains(matching.getProjectId());
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("삭제한 프로젝트는 더 이상 검색되지 않는다")
    void remove_thenNotFoundBySearch() {
        Project project = savedProject("삭제될 프로젝트 키워드테스트");
        adapter.index(project);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).contains(project.getProjectId()));

        adapter.remove(project.getProjectId());

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).doesNotContain(project.getProjectId()));
    }

    @Test
    @DisplayName("prefix로 시작하는 제목만 자동완성 후보로 나온다")
    void autocomplete_matchesTitlePrefix() {
        Project matching = savedProject("카카오 프로젝트");
        Project other = savedProject("완전히 다른 프로젝트");
        adapter.index(matching);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ProjectSuggestion> result = adapter.autocomplete("카카");
            assertThat(result).extracting(ProjectSuggestion::projectId).contains(matching.getProjectId());
            assertThat(result).extracting(ProjectSuggestion::projectId).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("영문 제목은 대소문자와 무관하게 매치된다")
    void autocomplete_caseInsensitive() {
        Project project = savedProject("Kakao Project");
        adapter.index(project);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ProjectSuggestion> result = adapter.autocomplete("kakao");
            assertThat(result).extracting(ProjectSuggestion::projectId).contains(project.getProjectId());
        });
    }

    @Test
    @DisplayName("매치가 후보 한도(50개)를 넘으면 50개로 잘린다")
    void autocomplete_limitsToCandidateLimit() {
        for (int i = 0; i < 55; i++) {
            adapter.index(savedProject("PrefixLimitTest Project " + i));
        }

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.autocomplete("PrefixLimitTest")).hasSize(50));
    }

    @Test
    @DisplayName("여러 단어로 검색하면 모든 단어가 prefix로 매치되는 프로젝트만 나온다")
    void autocomplete_multiWordQuery_matchesAllWordsAsPrefix() {
        Project matching = savedProject("고양이 밥 주는 기계");
        Project other = savedProject("강아지 사료 자동 급식기");
        adapter.index(matching);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(adapter.autocomplete("밥")).extracting(ProjectSuggestion::projectId).contains(matching.getProjectId());
            assertThat(adapter.autocomplete("고양이 밥")).extracting(ProjectSuggestion::projectId).contains(matching.getProjectId());
            assertThat(adapter.autocomplete("고양이 개")).extracting(ProjectSuggestion::projectId).doesNotContain(matching.getProjectId(), other.getProjectId());
        });
    }

    @Test
    @DisplayName("공백만 있는 검색어는 ES를 부르지 않고 빈 목록을 반환한다")
    void autocomplete_blankKeyword_returnsEmptyWithoutCallingEs() {
        assertThat(adapter.autocomplete("   ")).isEmpty();
    }

    @Test
    @DisplayName("nori 사전에 없는 속어가 흔한 조사 음절로 쪼개져도, 그 음절 하나만 겹치는 무관한 문서는 안 나온다")
    void search_oovSlangSplitIntoCommonParticle_doesNotMatchUnrelatedDocuments() {
        // "냥이"는 nori 사전에 없어 ["냥", "이"]로 쪼개진다. "이"는 "장인이"/"입니다"처럼 거의 모든
        // 자연스러운 한국어 문장에 등장하는 조사라, minimum_should_match 없이는 이 음절 하나만
        // 겹쳐도 매치된다(2026-08-19 실측 확인된 버그, 동일한 문장으로 재현).
        Project unrelated = Project.register(1L, null, "수제 가죽 노트커버", 1L,
                "장인이 한 땀 한 땀 만드는 가죽 노트커버 펀딩입니다.", "장인이 한 땀 한 땀 만드는 가죽 노트커버 펀딩입니다.",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(unrelated);
        savedProjectIds.add(saved.getProjectId());
        adapter.index(saved);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(adapter.search("냥이")).doesNotContain(saved.getProjectId()));
    }
}
