package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryTreeResponse;

import java.util.List;

public interface ProjectCategoryService {

    ProjectCategoryResponse create(ProjectCategoryCreateRequest request);

    /** create()가 JVM 락을 잡은 채로 트랜잭션을 커밋까지 마치기 위해 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    ProjectCategoryResponse createTransactional(ProjectCategoryCreateRequest request);

    /** 전체 카테고리를 트리 구조로 반환한다. */
    List<ProjectCategoryTreeResponse> findAllAsTree();

    ProjectCategoryResponse findById(Long projectCategoryId);

    ProjectCategoryResponse update(Long projectCategoryId, ProjectCategoryUpdateRequest request);

    /** update()가 JVM 락을 잡은 채로 트랜잭션을 커밋까지 마치기 위해 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    ProjectCategoryResponse updateTransactional(Long projectCategoryId, ProjectCategoryUpdateRequest request);

    void delete(Long projectCategoryId);

    /** delete()가 JVM 락을 잡은 채로 트랜잭션을 커밋까지 마치기 위해 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    void deleteTransactional(Long projectCategoryId);
}
