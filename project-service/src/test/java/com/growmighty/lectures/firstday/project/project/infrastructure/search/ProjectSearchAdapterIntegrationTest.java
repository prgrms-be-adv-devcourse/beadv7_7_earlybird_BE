package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.UUID;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
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
    @Autowired
    private ProjectCategoryRepository categoryRepository;
    @Autowired
    private RewardRepository rewardRepository;

    private final List<Long> savedProjectIds = new ArrayList<>();

    private Project savedProject(String title) {
        Project project = Project.register(1L, UUID.randomUUID(), null, title, 1L, "summary", "desc",
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
        // userdict_ko.txt에 없는 미등록 단어 "냥냥이"는 nori 사전에 없어 ["냥", "냥", "이"] 등으로 쪼개진다.
        // "이"는 "장인이"/"입니다"처럼 거의 모든 자연스러운 한국어 문장에 등장하는 조사라,
        // minimum_should_match 없이는 이 음절 하나만 겹쳐도 매치된다(동일 문장으로 재현 검증).
        Project unrelated = Project.register(1L, UUID.randomUUID(), null, "수제 가죽 노트커버", 1L,
                "장인이 한 땀 한 땀 만드는 가죽 노트커버 펀딩입니다.", "장인이 한 땀 한 땀 만드는 가죽 노트커버 펀딩입니다.",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(unrelated);
        savedProjectIds.add(saved.getProjectId());
        adapter.index(saved);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(adapter.search("냥냥이")).doesNotContain(saved.getProjectId()));
    }

    @Test
    @DisplayName("RRF 하이브리드 검색: 키워드 매치 프로젝트가 정상적으로 검색된다")
    void search_rrfHybrid_findsMatchingProjects() {
        Project matching1 = savedProject("인공지능 로봇 청소기 펀딩");
        Project matching2 = savedProject("초경량 무선 청소기 개발");
        Project other = savedProject("유기농 수제 쿠키 세트");

        adapter.index(matching1);
        adapter.index(matching2);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("청소기");
            assertThat(result).contains(matching1.getProjectId(), matching2.getProjectId());
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("제목/요약/본문에 없어도 검색어가 카테고리명과 정확히 일치하면 그 카테고리로 찾아진다")
    void search_matchesByExactCategoryName() {
        ProjectCategory category = categoryRepository.save(ProjectCategory.create(null, "액세서리"));
        Project matching = Project.register(1L, UUID.randomUUID(), null, "은은한 데일리룩", category.getId(), "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(matching);
        savedProjectIds.add(saved.getProjectId());
        // savedProject()의 categoryId(=1L, 실존하지 않는 더미 참조)와 우연히 겹치지 않도록, 방금 만든
        // category.getId() 바로 다음 값(역시 실존하지 않음)을 써서 other의 categoryId가 안 겹치게 한다.
        Project other = projectRepository.save(Project.register(1L, UUID.randomUUID(), null, "완전히 다른 내용의 프로젝트",
                category.getId() + 1, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30)));
        savedProjectIds.add(other.getProjectId());

        adapter.index(saved);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("액세서리");
            assertThat(result).contains(saved.getProjectId());
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("카테고리명이 아닌 일반 단어로 검색하면 카테고리 필터가 걸리지 않아, 서로 다른 카테고리의 결과가 함께 나온다")
    void search_nonCategoryKeyword_matchesAcrossDifferentCategories() {
        ProjectCategory bookCategory = categoryRepository.save(ProjectCategory.create(null, "도서"));
        ProjectCategory techCategory = categoryRepository.save(ProjectCategory.create(null, "전자기기"));
        Project travelEssay = Project.register(1L, UUID.randomUUID(), null, "여행 에세이집", bookCategory.getId(),
                "여행하며 쓴 글을 담은 에세이입니다.", "여행하며 쓴 글을 담은 에세이입니다.",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project travelProjector = Project.register(1L, UUID.randomUUID(), null, "여행용 빔프로젝터", techCategory.getId(),
                "여행지에서도 쓸 수 있는 소형 빔프로젝터입니다.", "여행지에서도 쓸 수 있는 소형 빔프로젝터입니다.",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project savedEssay = projectRepository.save(travelEssay);
        Project savedProjector = projectRepository.save(travelProjector);
        savedProjectIds.add(savedEssay.getProjectId());
        savedProjectIds.add(savedProjector.getProjectId());

        adapter.index(savedEssay);
        adapter.index(savedProjector);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // "여행"은 어떤 카테고리명과도 일치하지 않으므로 categoryId 필터가 안 걸리고,
            // 도서/전자기기 두 카테고리에 걸친 결과가 텍스트 매치만으로 그대로 나온다.
            List<Long> result = adapter.search("여행");
            assertThat(result).contains(savedEssay.getProjectId(), savedProjector.getProjectId());
        });
    }

    @Test
    @DisplayName("제목/요약/본문에 없어도 리워드명으로 검색하면 찾아진다")
    void search_matchesByRewardName() {
        Project matching = savedProject("은은한 데일리룩");
        rewardRepository.save(Reward.register(matching.getProjectId(), UUID.randomUUID(), "얼리버드 벌레 키링", "설명",
                BigDecimal.valueOf(3_000), 100));
        Project other = savedProject("완전히 다른 내용의 프로젝트");

        adapter.index(matching);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("키링");
            assertThat(result).contains(matching.getProjectId());
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("상위어(애완동물) 검색 시 하위 품목(고양이/강아지)이 모두 검색되고, 하위어(고양이) 검색 시 다른 하위어(강아지)는 오탐되지 않는다")
    void search_directionalSynonym_matchesSubCategoriesWithoutFalsePositives() {
        Project catProject = savedProject("고양이 원목 캣타워");
        Project dogProject = savedProject("강아지 수제 간식 세트");
        Project otherProject = savedProject("기계식 키보드 제작");

        adapter.index(catProject);
        adapter.index(dogProject);
        adapter.index(otherProject);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // 상위어 검색: 고양이, 강아지 프로젝트 모두 매치
            List<Long> petResult = adapter.search("애완동물");
            assertThat(petResult).contains(catProject.getProjectId(), dogProject.getProjectId());
            assertThat(petResult).doesNotContain(otherProject.getProjectId());

            // 하위어 검색: 해당 하위어만 매치되고 다른 하위어는 제외
            List<Long> catResult = adapter.search("고양이");
            assertThat(catResult).contains(catProject.getProjectId());
            assertThat(catResult).doesNotContain(dogProject.getProjectId(), otherProject.getProjectId());
        });
    }
}
