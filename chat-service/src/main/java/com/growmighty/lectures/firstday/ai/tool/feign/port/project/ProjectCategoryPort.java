package com.growmighty.lectures.firstday.ai.tool.feign.port.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectCategoryResult;

import java.util.List;

public interface ProjectCategoryPort {
    List<ProjectCategoryResult> findAllLeafCategories();
}
