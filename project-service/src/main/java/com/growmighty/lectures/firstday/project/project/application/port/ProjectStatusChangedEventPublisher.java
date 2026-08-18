package com.growmighty.lectures.firstday.project.project.application.port;

import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectStatusChangedEvent;

public interface ProjectStatusChangedEventPublisher {

    void publish(ProjectStatusChangedEvent event);
}
