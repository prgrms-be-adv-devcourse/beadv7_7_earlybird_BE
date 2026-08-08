package com.growmighty.lectures.firstday.project.project.infrastructure.search;

/** "이 프로젝트를 색인에서 지워야 한다"는 이벤트. */
record ProjectRemovedFromIndexEvent(Long projectId) {
}
