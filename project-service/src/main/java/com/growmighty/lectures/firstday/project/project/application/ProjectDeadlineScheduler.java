package com.growmighty.lectures.firstday.project.project.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감일(날짜)이 지났는데도 여전히 진행중인 프로젝트를 매일 자정에 찾아 성공/실패를 확정한다.
 * 프로젝트 기간이 일 단위(endAt이 LocalDate)라 "오늘 마감인 프로젝트"는 자정에 한 번만 처리해도
 * 정확하다 — endAt이 실수로 시각을 갖지 못하게 타입으로 막아뒀으니 지연 걱정 없이 하루 1회면 충분.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeadlineScheduler {

    private final ProjectService projectService;

    @Scheduled(cron = "0 0 0 * * *")
    public void closeExpiredProjects() {
        projectService.closeExpiredProjects();
    }
}
