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

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;   // ★ 추가

    @Transactional
    public ProjectInfo register(RegisterProjectCommand command) {
        Project project = Project.register(
            command.sellerId(), command.name(), command.price(), command.stockQuantity(), command.description());
        ProjectInfo info = ProjectInfo.from(projectRepository.save(project));
        eventPublisher.publishEvent(new ProjectChangedEvent(info.id()));   // ★
        return info;
    }

    @Transactional
    public ProjectInfo changePrice(Long projectId, BigDecimal newPrice) {
        Project project = getProjectEntity(projectId);
        project.changePrice(newPrice);
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));   // ★
        return ProjectInfo.from(project);
    }

    @Transactional
    public void decreaseStock(Long projectId, int quantity) {
        getProjectEntity(projectId).decreaseStock(quantity);
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));   // ★
    }

    @Transactional
    public void restoreStock(Long projectId, int quantity) {
        getProjectEntity(projectId).restoreStock(quantity);
        eventPublisher.publishEvent(new ProjectChangedEvent(projectId));   // ★
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
