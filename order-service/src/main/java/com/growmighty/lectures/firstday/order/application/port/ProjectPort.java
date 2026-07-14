package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.ProjectSnapshot;

public interface ProjectPort {

    ProjectSnapshot getProject(Long projectId);

    void decreaseStock(Long projectId, int quantity);

    void restoreStock(Long projectId, int quantity);
}
