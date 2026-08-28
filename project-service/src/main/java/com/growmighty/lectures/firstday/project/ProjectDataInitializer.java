package com.growmighty.lectures.firstday.project;

import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryTreeResponse;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 개발용 시드 데이터.
 * 프로젝트 카테고리, 초기 프로젝트 및 모든 프로젝트의 리워드를 자동으로 채워준다.
 *
 * 재기동마다 매번 실행되지만, 카테고리는 (부모, 이름)으로, 프로젝트는 title 기반 결정론적
 * idempotencyKey로 기존 데이터를 찾아 재사용한다 — 이미 만들어진 항목은 건너뛰고 빠진 항목만
 * 새로 채워서, 실행할 때마다 중복 생성 없이 시드 목록(buckets)에 맞춰 데이터가 수렴한다.
 *
 * <p>기본 비활성 — 시드를 채우려면 {@code project.seed.enabled=true}로 켠다.
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "project.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProjectDataInitializer implements CommandLineRunner {

    private final ProjectCategoryService projectCategoryService;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;

    /** (parentCategoryId, name) → categoryId. 재기동 시 카테고리를 재사용하기 위한 조회 캐시. */
    private final Map<String, Long> categoryIndex = new HashMap<>();

    @Override
    public void run(String... args) {
        initCategoriesAndProjects();
        ensureRewardsForAllProjects();
    }

    private void initCategoriesAndProjects() {
        indexExistingCategories();

        // 카테고리 계층 (루트 → 중분류 → 소분류). 프로젝트는 소분류(리프)에 매달린다.
        Long fashion = category(null, "패션");
        Long clothing = category(fashion, "의류");
        Long topCategoryId = category(clothing, "상의");
        Long bottomCategoryId = category(clothing, "하의");
        Long fashionGoods = category(fashion, "잡화");
        Long accessoryCategoryId = category(fashionGoods, "액세서리");

        Long tech = category(null, "전자기기");
        Long smartDeviceCategoryId = category(tech, "스마트기기");
        Long applianceCategoryId = category(tech, "생활가전");

        Long book = category(null, "도서·출판");
        Long essayCategoryId = category(book, "시·에세이");
        Long independentCategoryId = category(book, "독립출판");

        Long pet = category(null, "반려동물");
        Long petSupplyCategoryId = category(pet, "반려용품");

        List<SeedBucket> buckets = List.of(
                new SeedBucket(topCategoryId, "반팔 티셔츠", 3_000_000, "유기농 순면 100%로 제작한 데일리 아이템입니다.",
                        "오버사이즈", "크루넥", "브이넥", "스트라이프", "포켓", "롱슬리브", "무지 화이트", "무지 블랙", "레터링", "타이다이", "베이직"),
                new SeedBucket(topCategoryId, "울 혼방 롱코트", 5_000_000, "보온성 높은 클래식 핏으로 겨울을 따뜻하게 보내세요.",
                        "클래식 카멜", "블랙 미니멀", "체크 패턴", "더블브레스티드", "오버사이즈", "숏 기장", "롱 기장", "울 100%", "캐시미어 혼방", "후드 배기", "벨티드"),
                new SeedBucket(bottomCategoryId, "데님 팬츠", 4_000_000, "튼튼한 원단으로 오래 입을 수 있게 제작했습니다.",
                        "와이드 핏", "스트레이트 핏", "부츠컷", "스키니", "빈티지 워싱", "라이트 워싱", "블랙 워싱", "하이웨이스트", "크롭", "카고", "일자핏"),
                new SeedBucket(accessoryCategoryId, "가죽 반지갑", 2_000_000, "이탈리아산 베지터블 태닝 가죽으로 만들었습니다.",
                        "브라운", "블랙", "네이비", "카멜", "미니멀 카드형", "장지갑형", "지퍼형", "각인 가능", "빈티지 크랙", "스웨이드", "누드톤"),
                new SeedBucket(accessoryCategoryId, "실버 목걸이", 2_500_000, "군더더기 없는 미니멀 디자인의 순은 목걸이입니다.",
                        "미니멀 체인", "펜던트", "이니셜", "레이어드", "볼체인", "커플", "심플 링", "하트", "별", "타원", "십자가"),
                new SeedBucket(accessoryCategoryId, "가죽 이어폰 케이스", 1_500_000, "손바느질로 마감한 천연 가죽 케이스입니다.",
                        "에어팟용", "에어팟프로용", "무선이어폰용", "카라비너형", "키링형", "브라운", "블랙", "네이비", "각인 가능", "스트랩 포함", "미니 파우치형"),
                new SeedBucket(accessoryCategoryId, "가죽 워치 스트랩", 1_800_000, "부드러운 천연 가죽으로 손목을 편안하게 감쌉니다.",
                        "애플워치용", "갤럭시워치용", "브라운", "블랙", "탄색", "우븐", "클래식 버클", "마그네틱", "여름용 페브릭", "겨울용 퍼", "슬림"),
                new SeedBucket(accessoryCategoryId, "가죽 노트커버", 3_000_000, "장인이 한 땀 한 땀 스티칭한 가죽 커버입니다.",
                        "A5 사이즈", "A6 사이즈", "다이어리용", "여권 사이즈", "브라운", "블랙", "카멜", "각인 가능", "포켓 추가형", "탄성끈형", "지퍼형"),
                new SeedBucket(smartDeviceCategoryId, "빔프로젝터", 8_000_000, "캠핑에서도 쓸 수 있는 휴대성을 갖췄습니다.",
                        "미니", "초소형", "안드로이드 탑재", "배터리 내장", "4K 지원", "단초점", "휴대용", "천장형", "스피커 내장", "게이밍용", "홈시네마용"),
                new SeedBucket(applianceCategoryId, "공기청정기", 6_000_000, "좁은 공간에서도 빠르게 미세먼지를 잡아줍니다.",
                        "탁상용", "침실용", "차량용", "대형 거실용", "반려동물 특화", "헤파필터", "UV살균", "스마트 센서", "저소음", "미니", "USB충전형"),
                new SeedBucket(applianceCategoryId, "캡슐 커피머신", 4_500_000, "버튼 하나로 진한 스페셜티 커피를 즐기세요.",
                        "1인용", "미니", "휴대용", "무선", "캡슐호환형", "저소음", "스팀노즐형", "원터치", "사무실용", "캠핑용", "듀얼캡슐형"),
                new SeedBucket(essayCategoryId, "시집", 1_500_000, "신인 시인의 언어로 완성한 독립출판 시집입니다.",
                        "사랑을 노래하는", "계절을 담은", "청춘의", "위로가 되는", "새벽의", "바다를 담은", "고요한", "그리움의", "여행에서 쓴", "일상의", "첫"),
                new SeedBucket(essayCategoryId, "에세이집", 1_600_000, "솔직한 문장으로 담아낸 저자의 일상입니다.",
                        "여행", "육아", "직장인의", "1인가구", "퇴사 후", "요리", "산책", "고양이와 함께한", "제주살이", "느긋한 하루", "혼자 떠난"),
                new SeedBucket(independentCategoryId, "단편소설집", 1_500_000, "여러 편의 이야기를 한 권에 엮은 소설집입니다.",
                        "바다 이야기", "도시 미스터리", "SF", "청춘 성장", "옴니버스", "겨울", "골목길", "판타지", "추리", "일상 로맨스", "첫 출간"),
                new SeedBucket(petSupplyCategoryId, "자동 급식기", 10_000_000, "앱과 연동해 정시 정량으로 배식합니다.",
                        "고양이용", "강아지용", "다묘 가정용", "대형견용", "소형견용", "1인 가구용", "여행용 휴대형", "스마트 앱연동", "무소음", "저소음", "습식사료용"),
                new SeedBucket(petSupplyCategoryId, "산책줄", 3_000_000, "손목 부담을 줄여주는 탄성 소재로 제작했습니다.",
                        "소형견용", "중형견용", "대형견용", "강아지용", "고양이 하네스용", "야간 반사형", "충격흡수형", "자동 리드줄", "이중 안전형", "우천용 방수", "훈련용"),
                new SeedBucket(petSupplyCategoryId, "캣타워 스크래처", 3_500_000, "친환경 소재로 튼튼하게 만들었습니다.",
                        "고양이용", "다묘 가정용", "대형 캣타워형", "미니 스크래처형", "벽걸이형", "삼줄 소재", "골판지 소재", "원목 소재", "저소음", "콤팩트형", "리필형"),
                new SeedBucket(petSupplyCategoryId, "동결건조 간식", 2_800_000, "원물 100%, 무첨가로 안심하고 급여할 수 있습니다.",
                        "고양이용", "강아지용", "소형견용", "대형견용", "다묘 가정용", "노령묘용", "노령견용", "저알러지", "무염 무첨가", "닭가슴살", "연어")
        );

        for (SeedBucket bucket : buckets) {
            String[] variants = bucket.variants();
            for (int i = 0; i < variants.length; i++) {
                String title = variants[i] + " " + bucket.noun();
                long goalAmount = bucket.baseGoal() + (i * 200_000L);
                String description = title + " 펀딩입니다. " + bucket.supportSentence();
                openProject(bucket.categoryId(), title, goalAmount, description);
            }
        }
    }

    /** variants가 이 버킷의 제품군에 실제로 존재하는 옵션이어야 한다(공기청정기에 "핸드메이드" 같은 안 맞는 수식어 금지). */
    private record SeedBucket(Long categoryId, String noun, long baseGoal, String supportSentence, String... variants) {
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

    private void indexExistingCategories() {
        for (ProjectCategoryTreeResponse root : projectCategoryService.findAllAsTree()) {
            indexCategoryTree(root);
        }
    }

    private void indexCategoryTree(ProjectCategoryTreeResponse node) {
        categoryIndex.put(categoryKey(node.parentProjectCategoryId(), node.name()), node.id());
        for (ProjectCategoryTreeResponse child : node.children()) {
            indexCategoryTree(child);
        }
    }

    private String categoryKey(Long parentCategoryId, String name) {
        return parentCategoryId + ":" + name;
    }

    /** 이미 있는 (parent, name) 카테고리는 재사용하고, 없을 때만 새로 만든다 — 재기동마다 중복 생성 방지. */
    private Long category(Long parentCategoryId, String name) {
        String key = categoryKey(parentCategoryId, name);
        Long existingId = categoryIndex.get(key);
        if (existingId != null) {
            return existingId;
        }
        ProjectCategoryResponse category = projectCategoryService.create(new ProjectCategoryCreateRequest(parentCategoryId, name));
        categoryIndex.put(key, category.id());
        return category.id();
    }

    /**
     * 등록(PENDING_REVIEW) → 승인(IN_PROGRESS)까지 진행해 후원 가능한 상태로 만든다.
     * idempotencyKey를 title에서 결정론적으로 만들어(UUID.nameUUIDFromBytes), 재기동 시 같은 title이면
     * ProjectService.create()가 새로 만들지 않고 기존 프로젝트를 그대로 반환한다(이미 승인된 프로젝트를
     * 다시 approve()하면 상태 검증에서 예외가 나므로, 새로 만들어진 경우에만 승인한다).
     */
    private Long openProject(Long categoryId, String title, long goalAmount, String description) {
        LocalDateTime now = LocalDateTime.now();
        UUID idempotencyKey = UUID.nameUUIDFromBytes(title.getBytes(StandardCharsets.UTF_8));
        ProjectResponse project = projectService.create(1L, new ProjectCreateRequest(
                null, title, categoryId, description, description,
                BigDecimal.valueOf(goalAmount), now, LocalDate.now().plusDays(30), idempotencyKey));
        if (ProjectStatus.PENDING_REVIEW.name().equals(project.status())) {
            projectService.approve(project.projectId());
        }
        return project.projectId();
    }
}
