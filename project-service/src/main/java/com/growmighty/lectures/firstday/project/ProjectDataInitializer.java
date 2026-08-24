package com.growmighty.lectures.firstday.project;

import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 개발용 시드 데이터.
 * 프로젝트 카테고리, 초기 프로젝트 및 모든 프로젝트의 리워드를 자동으로 채워준다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ProjectDataInitializer implements CommandLineRunner {

    private final ProjectCategoryService projectCategoryService;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;

    @Override
    public void run(String... args) {
        if (projectCategoryService.findAllAsTree().isEmpty()) {
            initCategoriesAndProjects();
        }
        ensureRewardsForAllProjects();
    }

    private void initCategoriesAndProjects() {
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

        openProject(accessoryCategoryId, "수제 가죽 노트커버", 3_000_000,
                "장인이 한 땀 한 땀 만드는 A5 가죽 노트커버 펀딩입니다.");
        openProject(smartDeviceCategoryId, "휴대용 미니 빔프로젝터", 1_000,
                "캠핑에서도 쓰는 손바닥 크기 빔프로젝터.");
        openProject(bookCategoryId, "독립출판 시집 <새벽의 온도>", 1_500_000,
                "신인 시인, 강대혁의 첫 시집 인쇄 펀딩.");
        openProject(petSupplyCategoryId, "고양이 자동 급식기", 10_000_000,
                "집사 없이도 정시 배식. 앱 연동 자동 급식기.");
    }

    private void ensureRewardsForAllProjects() {
        List<Project> projects = projectRepository.findAll();
        int addedCount = 0;
        for (Project project : projects) {
            List<Reward> existingRewards = rewardRepository.findByProjectId(project.getProjectId());
            if (!existingRewards.isEmpty()) {
                continue;
            }
            seedRewardsForProject(project);
            addedCount++;
        }
        if (addedCount > 0) {
            log.info("[seed] {}개 프로젝트에 리워드 시드 데이터를 생성했습니다.", addedCount);
        }
    }

    private void seedRewardsForProject(Project project) {
        Long pid = project.getProjectId();
        String title = project.getTitle() != null ? project.getTitle() : "";

        if (title.contains("티셔츠")) {
            createReward(pid, "[얼리버드] 반팔 티셔츠 1장 (색상 선택)", "유기농 순면 100% 데일리 티셔츠", 19_000, 100);
            createReward(pid, "반팔 티셔츠 2장 세트 (교차 선택)", "데일리 티셔츠 2장 세트 구성", 35_000, 200);
            createReward(pid, "[패밀리팩] 반팔 티셔츠 4장 + 에코백", "가족/선물용 4장 세트 + 유기농 에코백 증정", 65_000, 50);
        } else if (title.contains("코트")) {
            createReward(pid, "[얼리버드] 클래식 롱코트", "보온성 높은 울 혼방 클래식 핏 롱코트", 149_000, 50);
            createReward(pid, "[풀패키지] 롱코트 + 캐시미어 머플러 세트", "롱코트와 프리미엄 캐시미어 머플러 구성", 189_000, 100);
        } else if (title.contains("데님") || title.contains("팬츠")) {
            createReward(pid, "[얼리버드] 빈티지 와이드 데님 팬츠", "90년대 감성의 빈티지 워싱 와이드 데님", 49_000, 100);
            createReward(pid, "데님 팬츠 + 소가죽 벨트 세트", "와이드 데님과 어울리는 통가죽 벨트 세트", 69_000, 150);
        } else if (title.contains("지갑")) {
            createReward(pid, "[얼리버드] 베지터블 가죽 반지갑", "이탈리아산 베지터블 태닝 가죽 반지갑", 39_000, 80);
            createReward(pid, "가죽 반지갑 + 이니셜 각인 서비스", "나만의 이니셜 각인 및 선물 포장 패키지", 49_000, 150);
        } else if (title.contains("목걸이")) {
            createReward(pid, "[얼리버드] 925 실버 목걸이", "군더더기 없는 미니멀 순은 목걸이", 29_000, 100);
            createReward(pid, "실버 목걸이 + 귀걸이 세트", "순은 목걸이와 매치되는 실버 귀걸이 세트", 49_000, 100);
        } else if (title.contains("이어폰")) {
            createReward(pid, "[얼리버드] 가죽 이어폰 케이스", "손바느질 천연 가죽 무선 이어폰 케이스", 19_000, 150);
            createReward(pid, "이어폰 케이스 + 가죽 키링 세트", "케이스와 세트 가죽 스트랩 키링", 29_000, 100);
        } else if (title.contains("워치") || title.contains("밴드")) {
            createReward(pid, "[얼리버드] 프리미엄 가죽 스트랩", "부드러운 천연 가죽 스마트워치 밴드", 25_000, 150);
            createReward(pid, "가죽 스트랩 + 메탈 스트랩 2종 세트", "기분과 착장에 따라 교체하는 스트랩 2종 세트", 45_000, 100);
        } else if (title.contains("빔프로젝터") || title.contains("프로젝터")) {
            createReward(pid, "[얼리버드] 미니 빔프로젝터 본체", "캠핑/가정용 초소형 미니 빔프로젝터", 189_000, 50);
            createReward(pid, "빔프로젝터 + 전용 삼각대", "각도 조절 삼각대 포함 세트", 229_000, 200);
            createReward(pid, "[풀패키지] 빔프로젝터 + 삼각대 + 80인치 스크린", "야외용 족자 스크린 및 수납 파우치 증정", 269_000, 100);
        } else if (title.contains("공기청정기")) {
            createReward(pid, "[얼리버드] 저소음 탁상용 공기청정기", "침실/원룸용 초저소음 공기청정기 본체", 59_000, 100);
            createReward(pid, "공기청정기 본체 + H13 헤파필터 2개", "1년치 정품 리필 필터 세트 포함", 79_000, 200);
        } else if (title.contains("커피")) {
            createReward(pid, "[얼리버드] 1인용 캡슐 커피머신", "초소형 컴팩트 캡슐 커피머신 본체", 69_000, 80);
            createReward(pid, "커피머신 + 스페셜티 캡슐 30개 세트", "다양한 풍미의 스페셜티 커피 캡슐 세트", 89_000, 150);
        } else if (title.contains("시집")) {
            createReward(pid, "시집 1권 (초판 한정 넘버링)", "신인 시인의 독립출판 시집 본권", 15_000, 500);
            createReward(pid, "시집 + 감성 필사 노트 세트", "시를 따라 쓰는 양장 필사 노트 포함", 25_000, 200);
            createReward(pid, "[후원자 패키지] 시집 + 필사노트 + 친필 서명본", "후원자 명단 수록 및 저자 친필 서명본", 35_000, 100);
        } else if (title.contains("에세이")) {
            createReward(pid, "에세이집 1권", "따뜻한 일상의 순간을 담은 에세이", 16_000, 300);
            createReward(pid, "에세이집 + 패브릭 북커버 세트", "책을 보호하는 감성 패브릭 북커버 증정", 26_000, 150);
        } else if (title.contains("소설")) {
            createReward(pid, "단편소설집 1권", "여섯 편의 바다 이야기를 담은 소설집", 15_000, 300);
            createReward(pid, "소설집 + 유리병 굿즈 + 북마크 세트", "소설 테마 굿즈 세트 포함", 24_000, 150);
        } else if (title.contains("급식기")) {
            createReward(pid, "[얼리버드] 스마트 자동 급식기 1대", "앱 연동 정시 정량 자동 급식기", 79_000, 80);
            createReward(pid, "급식기 1대 + 밀폐 사료통 세트", "사료 신선도를 유지하는 전용 보관통", 99_000, 300);
            createReward(pid, "[2묘 가구용] 급식기 2대 + 스테인리스 식기 세트", "다묘 가정을 위한 급식기 2대 세트", 179_000, 50);
        } else if (title.contains("목줄") || title.contains("산책")) {
            createReward(pid, "[얼리버드] 충격흡수 튼튼 산책줄", "손목 충격을 줄여주는 탄성 산책 리드줄", 22_000, 150);
            createReward(pid, "산책줄 + 배변봉투 케이스 + 하네스 세트", "안전하고 편안한 산책 풀세트", 39_000, 150);
        } else if (title.contains("스크래처")) {
            createReward(pid, "[얼리버드] 원목 캣타워 스크래처", "친환경 자작나무 원목 스크래처 타워", 49_000, 80);
            createReward(pid, "스크래처 타워 + 리필용 삼줄 2개 세트", "오래 쓸 수 있는 추가 리필 삼줄 세트", 65_000, 150);
        } else if (title.contains("간식")) {
            createReward(pid, "[얼리버드] 수제 동결건조 간식 3종 세트", "원물 100% 무첨가 동결건조 영양 간식", 24_000, 200);
            createReward(pid, "수제 간식 5종 세트 + 덴탈껌 팩", "영양 간식 5종과 구강 관리 덴탈껌 세트", 38_000, 200);
        } else if (title.contains("노트커버")) {
            createReward(pid, "[얼리버드] A5 가죽 노트커버 1개", "장인이 한 땀씩 스티칭한 가죽 노트커버", 29_000, 100);
            createReward(pid, "노트커버 1개 (색상 선택)", "브라운/블랙 중 색상 선택 가능", 35_000, 300);
            createReward(pid, "[풀패키지] 노트커버 + 프리미엄 만년필 세트", "각인 서비스 및 선물용 고급 케이스 포함", 95_000, 50);
        } else if (title.contains("캔들")) {
            createReward(pid, "[얼리버드] 천연 소이캔들 2종 세트", "우드심지와 천연 에센셜 오일 소이캔들", 23_000, 150);
            createReward(pid, "소이캔들 4종 세트 + 캔들 워머", "인기 향 4종과 감성 무드등 캔들 워머 세트", 45_000, 100);
        } else if (title.contains("비누")) {
            createReward(pid, "[얼리버드] 숙성 천연 비누 3종 세트", "콜드프로세스 숙성 무자극 천연 비누", 18_000, 150);
            createReward(pid, "천연 비누 5종 풀세트 + 원목 받침대", "5가지 천연 비누와 물빠짐 원목 받침대 세트", 29_000, 200);
        } else {
            createReward(pid, "[얼리버드] 기본 리워드", "프로젝트 기본 구성 리워드 상품", 25_000, 100);
            createReward(pid, "[스페셜] 프리미엄 풀패키지", "프로젝트 전체 구성 및 특별 굿즈 세트", 49_000, 50);
        }
    }

    private void createReward(Long projectId, String name, String description, long price, int quantity) {
        rewardRepository.save(Reward.register(projectId, UUID.randomUUID(), name, description, BigDecimal.valueOf(price), quantity));
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
                BigDecimal.valueOf(goalAmount), now, LocalDate.now().plusDays(30), UUID.randomUUID()));
        projectService.approve(project.projectId());
        return project.projectId();
    }
}

