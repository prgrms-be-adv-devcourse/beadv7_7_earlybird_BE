package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compatibility Layer 품질 및 False Positive / False Negative 정밀 검증 테스트.
 *
 * <p>검증 항목:
 * 1. 복합 문맥 ("여름에도 착용 가능한 썸머 울 셔츠", "사계절 착용 가능한 옥스포드 셔츠" 등)
 * 2. 단순 연관어("울", "롱코트") vs 명확한 반대어("겨울", "방한") 구분
 * 3. 10대 자연어 쿼리 세트 평가 (Top 결과 및 순위 변화)
 * 4. False Positive / False Negative 발생 여부 추적
 */
class CompatibilityQualityDeepEvaluationTest {

    private final QueryProductCompatibilityEvaluator evaluator = new QueryProductCompatibilityEvaluator();

    private ProjectDocument doc(Long id, String title, String summary, String desc) {
        return new ProjectDocument(id, title, summary, desc, 1L, List.of(),
                new float[1536], new float[1536], new float[1536], new float[1536], new float[1536]);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. 복합 문맥 검증: "여름용 옷" 검색에서 False Negative가 발생하는가?
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[False Negative 검증] '여름에도 착용 가능한 썸머 울 셔츠'가 '울' 키워드 때문에 억울하게 Conflict 감점되는가?")
    void test_summerWool_falseNegative_check() {
        Requirement summerReq = new Requirement(
                "여름용",
                "usage_or_context",
                true,
                List.of("겨울", "방한", "기모", "울 롱코트", "패딩")
        );
        QueryIntent intent = new QueryIntent(
                "옷", List.of(summerReq), "옷", "여름", null, null, null, null,
                List.of("여름용"), List.of(), "여름용 쿨링 반팔 셔츠"
        );

        // 상품: 여름용 썸머 울 셔츠 (여름 만족 + '울' 단어 포함)
        ProjectDocument summerWoolShirt = doc(1L, "여름 린넨 썸머 울 반팔 셔츠", "여름철 시원하게 입는 프리미엄 썸머 울 소재", "통기성이 우수한 여름 셔츠");

        var result = evaluator.evaluate(summerWoolShirt, intent);

        System.out.println("\n[Evaluation 1] 여름 썸머 울 셔츠 평가 결과:");
        System.out.println("  Satisfaction: " + result.satisfactionScore());
        System.out.println("  Conflict:     " + result.conflictScore());
        System.out.println("  Adjustment:   " + result.totalAdjustment());
        System.out.println("  Reason:       " + result.reason());

        // '울 롱코트'는 polarOpposites에 있지만 '울' 단독은 아니므로 오탐이 없어야 함
        // 또한 '여름' 만족이 있으므로 Conflict = 0.0, Adjustment > 0 이어야 함 (False Negative 방지)
        assertThat(result.conflictScore()).isEqualTo(0.0);
        assertThat(result.totalAdjustment()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("[False Negative 검증] '사계절용 옷' 검색 시 올시즌 의류가 감점되지 않고 적절히 평가되는가?")
    void test_allSeasonClothes_evaluation() {
        Requirement allSeasonReq = new Requirement(
                "사계절용",
                "usage_or_context",
                false,
                List.of("특정계절전용", "한겨울전용", "한여름전용")
        );
        QueryIntent intent = new QueryIntent(
                "옷", List.of(allSeasonReq), "옷", null, null, "사계절", null, null,
                List.of(), List.of("사계절용"), "사계절 데일리 셔츠 의류"
        );

        ProjectDocument allSeasonShirt = doc(1L, "사계절 데일리 옥스포드 셔츠", "봄 여름 가을 겨울 사계절 착용 가능", "기본 베이직 셔츠");
        var result = evaluator.evaluate(allSeasonShirt, intent);

        System.out.println("\n[Evaluation 2] 사계절 데일리 셔츠 평가 결과:");
        System.out.println("  Satisfaction: " + result.satisfactionScore());
        System.out.println("  Conflict:     " + result.conflictScore());
        System.out.println("  Adjustment:   " + result.totalAdjustment());

        assertThat(result.satisfactionScore()).isGreaterThan(0.5);
        assertThat(result.conflictScore()).isEqualTo(0.0);
        assertThat(result.totalAdjustment()).isGreaterThan(0.0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. 10대 검색어 세트 종합 시뮬레이션
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[10대 쿼리 세트 종합 평가] 다양한 자연어 검색어에서 충돌 상품과 부합 상품의 점수 격차 검증")
    void test_10QuerySet_comprehensive_evaluation() {
        // 1. 여름용 옷
        evaluateQuery("1. 여름용 옷",
                new Requirement("여름용", "usage_or_context", true, List.of("겨울", "방한", "기모", "울 롱코트", "패딩")),
                doc(101L, "여름 린넨 쿨링 반팔 티셔츠", "시원한 여름 의류", "여름용 반팔"),
                doc(102L, "겨울 프리미엄 울 방한 롱코트", "추운 겨울용 코트", "겨울 방한 의류"));

        // 2. 겨울용 옷
        evaluateQuery("2. 겨울용 옷",
                new Requirement("겨울용", "usage_or_context", true, List.of("여름", "반팔", "린넨", "쿨링")),
                doc(201L, "겨울 방한 오버핏 울 롱코트", "따뜻한 겨울용 코트", "겨울철 패딩 코트"),
                doc(202L, "여름 쿨링 메쉬 반팔 티셔츠", "더운 여름을 위한 의류", "시원한 여름 반팔"));

        // 3. 가벼운 출퇴근용 가방
        evaluateQuery("3. 가벼운 출퇴근용 가방",
                new Requirement("가벼운", "characteristic", false, List.of("무거운", "헤비")),
                doc(301L, "초경량 직장인 통근 백팩 가방", "출퇴근에 편한 가벼운 백팩", "가벼운 노트북 가방"),
                doc(302L, "초대형 무거운 하드케이스 여행용 캐리어", "대용량 무거운 짐 보관", "장기 여행 캐리어"));

        // 4. 조용한 사무용 키보드
        evaluateQuery("4. 조용한 사무용 키보드",
                new Requirement("조용한", "characteristic", true, List.of("청축", "소음", "시끄러운")),
                doc(401L, "저소음 적축 오피스 사무용 키보드", "무소음 타건감 키보드", "사무실용 조용한 키보드"),
                doc(402L, "RGB 청축 게이밍 기계식 키보드", "찰칵거리는 경쾌한 타건음", "시끄러운 클릭 스위치"));

        // 5. 초보자용 카메라
        evaluateQuery("5. 초보자용 카메라",
                new Requirement("초보자용", "target_audience", true, List.of("전문가용", "상급자", "프로페셔널", "시네마")),
                doc(501L, "초보자 입문용 미러리스 카메라", "쉬운 조작법의 가벼운 카메라", "카메라 입문 추천"),
                doc(502L, "프로페셔널 시네마 8K 전문가용 카메라", "상급자 전용 시네마 카메라", "전문 방송용 카메라"));

        // 6. 원룸용 작은 책상
        evaluateQuery("6. 원룸용 작은 책상",
                new Requirement("작은", "size_or_weight", true, List.of("초대형", "대형", "와이드", "거실용")),
                doc(601L, "원룸용 미니멀 작은 책상", "공간 절약형 소형 책상", "작은 방 인테리어 책상"),
                doc(602L, "대형 와이드 중역용 거실 회의실 책상", "초대형 8인용 원목 테이블", "대형 회의 테이블"));

        // 7. 휴대하기 좋은 노트북
        evaluateQuery("7. 휴대하기 좋은 노트북",
                new Requirement("휴대하기 좋은", "characteristic", false, List.of("거치형", "무거운", "헤비", "대형")),
                doc(701L, "초경량 슬림 휴대용 노트북", "휴대하기 좋은 가벼운 무게", "출장용 슬림 노트북"),
                doc(702L, "헤비 게이밍 거치형 워크스테이션 노트북", "무거운 데스크탑급 사양", "거치형 고사양 노트북"));

        // 8. 사계절용 옷
        evaluateQuery("8. 사계절용 옷",
                new Requirement("사계절용", "usage_or_context", false, List.of("한겨울전용", "한여름전용")),
                doc(801L, "사계절 올시즌 데일리 코튼 셔츠", "사계절 착용 가능한 의류", "올시즌 셔츠"),
                doc(802L, "한겨울전용 극세사 기모 방한복", "한겨울전용 방한 내의", "겨울 전용"));

        // 9. 편하게 입는 옷
        evaluateQuery("9. 편하게 입는 옷",
                new Requirement("편하게", "preference", false, List.of("불편한", "타이트한", "코르셋")),
                doc(901L, "편하게 입는 오버핏 이지웨어 홈웨어", "편안한 착용감의 데일리 의류", "루즈핏 티셔츠"),
                doc(902L, "보정용 코르셋 바디수트", "타이트한 핏의 보정 의류", "밀착 착용"));

        // 10. 고급스러운 옷
        evaluateQuery("10. 고급스러운 옷",
                new Requirement("고급스러운", "preference", false, List.of("저가형", "일회용", "싸구려")),
                doc(1001L, "프리미엄 실크 혼방 고급스러운 수트", "고급스러운 실루엣의 명품 정장", "하이엔드 의류"),
                doc(1002L, "행사용 일회용 저가형 우비", "저가형 싸구려 비닐 옷", "일회용 의류"));
    }

    private void evaluateQuery(String queryName, Requirement req, ProjectDocument targetMatchDoc, ProjectDocument conflictDoc) {
        QueryIntent intent = new QueryIntent(
                "item", List.of(req), "item", null, null, null, null, null,
                List.of(), List.of(), queryName
        );

        var matchResult = evaluator.evaluate(targetMatchDoc, intent);
        var conflictResult = evaluator.evaluate(conflictDoc, intent);

        System.out.printf("\n=== [%s] ===\n", queryName);
        System.out.printf("  [부합 상품] %-30s | Sat=%.2f, Conf=%.2f -> Total Adj= %+.4f\n",
                targetMatchDoc.title(), matchResult.satisfactionScore(), matchResult.conflictScore(), matchResult.totalAdjustment());
        System.out.printf("  [충돌 상품] %-30s | Sat=%.2f, Conf=%.2f -> Total Adj= %+.4f\n",
                conflictDoc.title(), conflictResult.satisfactionScore(), conflictResult.conflictScore(), conflictResult.totalAdjustment());

        // 부합 상품은 점수 가점 또는 중립, 충돌 상품은 감점을 받아야 함
        assertThat(matchResult.totalAdjustment())
                .as("[%s] 부합 상품 점수가 충돌 상품 점수보다 높아야 함", queryName)
                .isGreaterThan(conflictResult.totalAdjustment());

        assertThat(conflictResult.totalAdjustment())
                .as("[%s] 충돌 상품은 패널티를 받아 음수여야 함", queryName)
                .isLessThan(0.0);
    }
}
