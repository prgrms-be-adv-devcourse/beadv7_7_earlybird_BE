package com.growmighty.lectures.firstday.project;

import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 개발용 시드 데이터.
 * 리워드 ID는 등록 순서대로 전역 시퀀스가 매겨진다 — orders.http 가 rewardId=1 을 가정하므로
 * 등록 순서를 바꾸지 말 것 (프로젝트 1 → 리워드 1~3, 프로젝트 2 → 리워드 4~5, ...).
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ProjectDataInitializer implements CommandLineRunner {
    private final ProjectCategoryService projectCategoryService;
    private final ProjectService projectService;
    private final RewardService rewardService;

    @Override
    public void run(String... args) {
        // ddl-auto: update 라 재시작해도 테이블이 비워지지 않는다 — 이미 시드가 들어가 있으면 건너뛴다.
        if (!projectCategoryService.findAllAsTree().isEmpty()) {
            return;
        }

        // 카테고리 계층 (루트 → 중분류 → 소분류). 프로젝트는 소분류(리프)에 매달린다.
        Long fashion = category(null, "패션");
        Long clothing = category(fashion, "의류");
        category(clothing, "상의");
        category(clothing, "하의");
        Long fashionGoods = category(fashion, "잡화");
        Long accessoryCategoryId = category(fashionGoods, "액세서리");

        Long tech = category(null, "전자기기");
        Long smartDeviceCategoryId = category(tech, "스마트기기");
        category(tech, "생활가전");

        Long book = category(null, "도서·출판");
        Long bookCategoryId = category(book, "시·에세이");
        category(book, "독립출판");

        Long pet = category(null, "반려동물");
        Long petSupplyCategoryId = category(pet, "반려용품");

        // 프로젝트 1 (rewardId 1~4)
        Long p1 = openProject(accessoryCategoryId, "수제 가죽 노트커버", 3_000_000,
            "장인이 한 땀 한 땀 만드는 A5 가죽 노트커버 펀딩입니다.");
        reward(p1, "[얼리버드] 노트커버 1개", 29_000, 100, "브라운 단일 색상, 8월 말 발송 예정");
        reward(p1, "노트커버 1개", 35_000, 300, "색상 선택 가능");
        reward(p1, "노트커버 2개 세트", 65_000, 150, "선물용 패키지 포함");
        reward(p1, "[풀패키지] 노트커버 + 프리미엄 만년필 세트", 95_000, 50, "각인 서비스 및 선물용 고급 케이스 포함");

        // 프로젝트 2 (rewardId 5~7)
        Long p2 = openProject(smartDeviceCategoryId, "휴대용 미니 빔프로젝터", 1_000,
            "캠핑에서도 쓰는 손바닥 크기 빔프로젝터.");
        reward(p2, "[얼리버드] 빔프로젝터", 189_000, 50, "선착순 한정 특가");
        reward(p2, "빔프로젝터 + 삼각대", 229_000, 200, "전용 미니 삼각대 포함");
        reward(p2, "[풀패키지] 빔프로젝터 + 삼각대 + 80인치 족자 스크린", 269_000, 100, "야외용 수납 가방 증정");

        // 프로젝트 3 (rewardId 8~10)
        Long p3 = openProject(bookCategoryId, "독립출판 시집 <새벽의 온도>", 1_500_000,
            "신인 시인, 강대혁의 첫 시집 인쇄 펀딩.");
        reward(p3, "시집 1권", 15_000, 500, "초판 한정 넘버링");
        reward(p3, "시집 + 필사 노트", 25_000, 200, "굿즈 세트");
        reward(p3, "[후원자 패키지] 시집 + 필사 노트 + 저자 친필 서명 엽서", 35_000, 100, "후원자 명단 시집 수록");

        // 프로젝트 4 (rewardId 11~13)
        Long p4 = openProject(petSupplyCategoryId, "고양이 자동 급식기", 10_000_000,
            "집사 없이도 정시 배식. 앱 연동 자동 급식기.");
        reward(p4, "[얼리버드] 급식기 1대", 79_000, 80, "화이트 단일 색상");
        reward(p4, "급식기 1대 + 전용 사료통", 99_000, 300, "색상 선택 가능");
        reward(p4, "[2묘 가구용] 급식기 2대 + 스테인리스 식기 2개 세트", 179_000, 50, "추가 위생 식기 세트 증정");
    }

    private Long category(Long parentCategoryId, String name) {
        ProjectCategoryResponse category = projectCategoryService.create(new ProjectCategoryCreateRequest(parentCategoryId, name));
        return category.id();
    }

    /** 등록(PENDING_REVIEW) → 승인(IN_PROGRESS)까지 진행해 후원 가능한 상태로 만든다 */
    private Long openProject(Long categoryId, String title, long goalAmount, String description) {
        LocalDateTime now = LocalDateTime.now();
        ProjectResponse project = projectService.create(1L, new ProjectCreateRequest(
            null, title, categoryId, description, description,
            BigDecimal.valueOf(goalAmount), now, LocalDate.now().plusDays(30)));
        projectService.approve(project.projectId());
        return project.projectId();
    }

    private void reward(Long projectId, String name, long price, int quantity, String desc) {
        rewardService.register(projectId, 1L, new RewardCreateRequest(
            name, desc, BigDecimal.valueOf(price), quantity));
    }
}
