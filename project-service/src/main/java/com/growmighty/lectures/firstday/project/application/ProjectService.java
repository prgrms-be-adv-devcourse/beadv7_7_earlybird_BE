package com.growmighty.lectures.firstday.project.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.application.dto.ProjectInfo;
import com.growmighty.lectures.firstday.project.application.dto.RegisterProjectCommand;
import com.growmighty.lectures.firstday.project.domain.event.ProjectChangedEvent;
import com.growmighty.lectures.firstday.project.domain.Project;
import com.growmighty.lectures.firstday.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProjectInfo register(RegisterProjectCommand command) {
        Project project = Project.register(
            command.creatorId(), command.title(), command.description(),
            command.goalAmount(), command.startAt(), command.endAt());
        ProjectInfo info = ProjectInfo.from(projectRepository.save(project));
        eventPublisher.publishEvent(new ProjectChangedEvent(info.id()));
        return info;
    }

    /** 창작자: 심사 요청 (DRAFT → IN_REVIEW) */
    @Transactional
    public ProjectInfo submitForReview(Long projectId) {
        Project project = getProjectEntity(projectId);
        project.submitForReview();
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));
        return ProjectInfo.from(project);
    }

    /** 관리자: 심사 승인 (IN_REVIEW → OPEN). TODO(팀): 관리자 권한 검증은 Admin 컨텍스트 확정 후 */
    @Transactional
    public ProjectInfo approve(Long projectId) {
        Project project = getProjectEntity(projectId);
        project.approve();
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));
        return ProjectInfo.from(project);
    }

    /** 관리자: 심사 반려 (IN_REVIEW → REJECTED) */
    @Transactional
    public ProjectInfo reject(Long projectId) {
        Project project = getProjectEntity(projectId);
        project.reject();
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));
        return ProjectInfo.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectInfo getProjectInfo(Long projectId) {
        return ProjectInfo.from(getProjectEntity(projectId));
    }

    private Project getProjectEntity(Long projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
    }
}
