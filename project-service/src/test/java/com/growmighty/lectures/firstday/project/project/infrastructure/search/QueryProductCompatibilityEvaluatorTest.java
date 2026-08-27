package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QueryProductCompatibilityEvaluator} 단위 테스트.
 *
 * <p>3대 신호(Relevance, Satisfaction, Conflict) 분리 및 다양한 검색어/상품 도메인에서의
 * 범용적 적합성/충돌 판별 동작을 검증한다.
 */
class QueryProductCompatibilityEvaluatorTest {

    private final QueryProductCompatibilityEvaluator evaluator = new QueryProductCompatibilityEvaluator();

    private ProjectDocument document(Long id, String title, String summary, String desc) {
        return new ProjectDocument(id, title, summary, desc, 1L, List.of(),
                new float[1536], new float[1536], new float[1536], new float[1536], new float[1536]);
    }

    @Test
    @DisplayName("[여름용 옷] 린넨 반팔 티셔츠 vs 기본 면 반팔 vs 겨울 롱코트 vs 사계절 셔츠")
    void evaluate_summerClothes_differentProducts() {
        Requirement summerReq = new Requirement(
                "여름용",
                "usage_or_context",
                true,
                List.of("겨울", "방한", "기모", "울 롱코트", "패딩")
        );
        QueryIntent intent = new QueryIntent(
                "옷",
                List.of(summerReq),
                "옷", "여름", null, null, null, null,
                List.of("여름용"), List.of(),
                "여름 반팔 쿨링 티셔츠 의류"
        );

        // Product A: 여름용 린넨 반팔 티셔츠
        ProjectDocument productA = document(1L, "여름용 린넨 반팔 티셔츠", "시원한 여름 린넨 소재", "통기성이 뛰어난 쿨링 반팔");
        var resultA = evaluator.evaluate(productA, intent);

        // Product B: 기본 면 반팔 티셔츠
        ProjectDocument productB = document(2L, "기본 베이직 순면 반팔 티셔츠", "데일리로 입기 좋은 면 티셔츠", "깔끔한 핏의 순면 반팔");
        var resultB = evaluator.evaluate(productB, intent);

        // Product C: 겨울용 울 혼방 롱코트
        ProjectDocument productC = document(3L, "겨울용 프리미엄 울 혼방 롱코트", "한겨울 방한용 오버핏 롱코트", "따뜻한 울 소재의 겨울 필수 코트");
        var resultC = evaluator.evaluate(productC, intent);

        // Product D: 사계절용 셔츠
        ProjectDocument productD = document(4L, "사계절 올시즌 데일리 옥스포드 셔츠", "사계절 내내 편안한 셔츠", "봄 여름 가을 겨울 착용 가능");
        var resultD = evaluator.evaluate(productD, intent);

        // Assertions
        // 1. 여름 전용 의류(A)는 Satisfaction이 높고 가점(+)을 받는다
        assertThat(resultA.satisfactionScore()).isGreaterThan(0.5);
        assertThat(resultA.conflictScore()).isEqualTo(0.0);
        assertThat(resultA.totalAdjustment()).isGreaterThan(0.0);

        // 2. 기본 면 반팔(B)은 충돌 없음(0.0), 중립 또는 약한 연관
        assertThat(resultB.conflictScore()).isEqualTo(0.0);
        assertThat(resultB.totalAdjustment()).isGreaterThanOrEqualTo(0.0);

        // 3. 겨울 울 롱코트(C)는 명확한 Conflict가 감지되어 강력한 감점(-)을 받는다
        assertThat(resultC.conflictScore()).isGreaterThan(0.8);
        assertThat(resultC.totalAdjustment()).isLessThan(-0.2);

        // 4. 점수 우위 관계 검증: A > B >= D > C
        assertThat(resultA.totalAdjustment()).isGreaterThan(resultB.totalAdjustment());
        assertThat(resultB.totalAdjustment()).isGreaterThan(resultC.totalAdjustment());
        assertThat(resultD.totalAdjustment()).isGreaterThan(resultC.totalAdjustment());
    }

    @Test
    @DisplayName("[조용한 사무용 키보드] 저소음 적축 키보드 vs 청축 게이밍 키보드")
    void evaluate_quietKeyboard_identifiesConflict() {
        Requirement quietReq = new Requirement(
                "조용한",
                "characteristic",
                true,
                List.of("청축", "타건음이 큰", "소음", "시끄러운", "찰칵")
        );
        Requirement officeReq = new Requirement(
                "사무용",
                "usage_or_context",
                false,
                List.of("게이밍 전용", "피시방")
        );

        QueryIntent intent = new QueryIntent(
                "키보드",
                List.of(quietReq, officeReq),
                "키보드", null, null, "사무용", null, null,
                List.of("조용한", "사무용"), List.of(),
                "조용한 사무용 저소음 키보드"
        );

        // Product A: 저소음 무소음 사무용 키보드
        ProjectDocument quietKeyboard = document(10L, "무소음 저소음 적축 사무용 기계식 키보드", "오피스 키보드", "사무실에서 쓰기 좋은 키보드");
        var resultQuiet = evaluator.evaluate(quietKeyboard, intent);

        // Product B: 청축 게이밍 키보드 (소음 발생)
        ProjectDocument clickyKeyboard = document(20L, "화려한 RGB 청축 기계식 게이밍 키보드", "찰칵거리는 경쾌한 타건음", "게이머를 위한 청축 키보드");
        var resultClicky = evaluator.evaluate(clickyKeyboard, intent);

        assertThat(resultQuiet.satisfactionScore()).isGreaterThan(0.0);
        assertThat(resultQuiet.conflictScore()).isEqualTo(0.0);
        assertThat(resultQuiet.totalAdjustment()).isGreaterThan(0.0);

        assertThat(resultClicky.conflictScore()).isGreaterThan(0.4);
        assertThat(resultClicky.totalAdjustment()).isLessThan(0.0);
    }

    @Test
    @DisplayName("[초보자용 카메라] 입문자용 미러리스 카메라 vs 전문가용 풀프레임 시네마 카메라")
    void evaluate_beginnerCamera_identifiesConflict() {
        Requirement req = new Requirement(
                "초보자용",
                "target_audience",
                true,
                List.of("전문가용", "상급자", "프로페셔널", "시네마 전용", "하이엔드")
        );
        QueryIntent intent = new QueryIntent(
                "카메라",
                List.of(req),
                "카메라", null, null, null, "초보자", null,
                List.of("초보자용"), List.of(),
                "초보자 입문용 미러리스 카메라"
        );

        ProjectDocument beginnerCam = document(1L, "초보자용 가벼운 입문용 미러리스 카메라", "처음 시작하는 사람을 위한 카메라", "쉬운 조작법");
        ProjectDocument proCam = document(2L, "전문가용 풀프레임 8K 시네마 카메라", "프로페셔널 영상 감독을 위한 하이엔드 카메라", "전문 상급자용 장비");

        var resultBeg = evaluator.evaluate(beginnerCam, intent);
        var resultPro = evaluator.evaluate(proCam, intent);

        assertThat(resultBeg.totalAdjustment()).isGreaterThan(0.0);
        assertThat(resultPro.conflictScore()).isGreaterThan(0.7);
        assertThat(resultPro.totalAdjustment()).isLessThan(0.0);
    }

    @Test
    @DisplayName("[단순 검색어 '노트북'] Requirements가 없는 경우 완벽한 중립(0.0) 반환")
    void evaluate_noRequirements_returnsNeutral() {
        QueryIntent simpleIntent = QueryIntent.passThrough("노트북");
        ProjectDocument laptop = document(1L, "초경량 노트북", "휴대용 노트북", "가벼운 무게");

        var result = evaluator.evaluate(laptop, simpleIntent);

        assertThat(result.totalAdjustment()).isEqualTo(0.0);
        assertThat(result.satisfactionScore()).isEqualTo(0.0);
        assertThat(result.conflictScore()).isEqualTo(0.0);
    }
}
