package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지 응답. Spring의 {@link Page}를 그대로 직렬화하면 pageable/sort 같은 내부 구조까지
 * 응답에 섞여 나가므로, 클라이언트가 실제로 쓰는 필드만 추린다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    /**
     * 이미 메모리에 올라온 전체 목록을 잘라 페이지로 만든다 — 키워드 검색 경로처럼 정렬
     * 순서가 DB가 아니라 ES 관련도에서 오는 경우에 쓴다.
     */
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(all.subList(from, to), page, size, totalPages, all.size());
    }
}
