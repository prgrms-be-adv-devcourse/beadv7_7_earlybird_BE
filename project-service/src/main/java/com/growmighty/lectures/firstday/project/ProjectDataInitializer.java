package com.growmighty.lectures.firstday.project;

import com.growmighty.lectures.firstday.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.application.RewardService;
import com.growmighty.lectures.firstday.project.application.dto.ProjectInfo;
import com.growmighty.lectures.firstday.project.application.dto.RegisterProjectCommand;
import com.growmighty.lectures.firstday.project.application.dto.RegisterRewardCommand;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final ProjectService projectService;
    private final RewardService rewardService;
    private final ProjectCategoryService projectCategoryService;

    private Long lifeCategoryId;

    @Override
    public void run(String... args) {
        seedCategories();

        // 프로젝트 1 (rewardId 1~3)
        Long p1 = openProject("수제 가죽 노트커버", 3_000_000,
            "장인이 한 땀 한 땀 만드는 A5 가죽 노트커버 펀딩입니다.");
        reward(p1, "[얼리버드] 노트커버 1개", 29_000, 100, "브라운 단일 색상, 8월 말 발송 예정");
        reward(p1, "노트커버 1개", 35_000, 300, "색상 선택 가능");
        reward(p1, "노트커버 2개 세트", 65_000, 150, "선물용 패키지 포함");

        // 프로젝트 2 (rewardId 4~5)
        Long p2 = openProject("휴대용 미니 빔프로젝터", 20_000_000,
            "캠핑에서도 쓰는 손바닥 크기 빔프로젝터.");
        reward(p2, "[얼리버드] 빔프로젝터", 189_000, 50, "선착순 한정 특가");
        reward(p2, "빔프로젝터 + 삼각대", 229_000, 200, "전용 미니 삼각대 포함");

        // 프로젝트 3 (rewardId 6~7)
        Long p3 = openProject("독립출판 시집 <새벽의 온도>", 1_500_000,
            "신인 시인의 첫 시집 인쇄 펀딩.");
        reward(p3, "시집 1권", 15_000, 500, "초판 한정 넘버링");
        reward(p3, "시집 + 필사 노트", 25_000, 200, "굿즈 세트");

        // 프로젝트 4 (rewardId 8~9)
        Long p4 = openProject("고양이 자동 급식기", 10_000_000,
            "집사 없이도 정시 배식. 앱 연동 자동 급식기.");
        reward(p4, "[얼리버드] 급식기 1대", 79_000, 80, "화이트 단일 색상");
        reward(p4, "급식기 1대 + 전용 사료통", 99_000, 300, "색상 선택 가능");
    }

    /** 대분류 1개 + 소분류 1개만 시드로 넣는다. 카테고리 전체 체계는 관리자 기능 확정 후 채운다. */
    private void seedCategories() {
        ProjectCategoryResponse root = projectCategoryService.create(new ProjectCategoryCreateRequest(null, "라이프스타일"));
        ProjectCategoryResponse child = projectCategoryService.create(new ProjectCategoryCreateRequest(root.id(), "생활용품"));
        this.lifeCategoryId = child.id();
    }

    /** 등록 → 심사 요청 → 승인(공개)까지 진행해 후원 가능한 상태로 만든다 */
    private Long openProject(String title, long goalAmount, String description) {
        LocalDateTime now = LocalDateTime.now();
        ProjectInfo info = projectService.register(new RegisterProjectCommand(
            1L, lifeCategoryId, title, description, BigDecimal.valueOf(goalAmount), now, now.plusDays(30)));
        projectService.submitForReview(info.id());
        projectService.approve(info.id());
        return info.id();
    }

    private void reward(Long projectId, String name, long price, int quantity, String desc) {
        rewardService.register(new RegisterRewardCommand(
            projectId, name, desc, BigDecimal.valueOf(price), quantity));
    }
}
