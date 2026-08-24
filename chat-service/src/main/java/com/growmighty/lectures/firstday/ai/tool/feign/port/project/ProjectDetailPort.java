package com.growmighty.lectures.firstday.ai.tool.feign.port.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectDetailResult;

public interface ProjectDetailPort {
    ProjectDetailResult findById(Long projectId);
}
