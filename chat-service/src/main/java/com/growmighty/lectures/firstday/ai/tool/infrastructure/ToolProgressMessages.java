package com.growmighty.lectures.firstday.ai.tool.infrastructure;

import java.util.Map;

// tool_start 이벤트에 실어보낼 진행형/완료형 문구. FE는 tool_start마다 진행형을 스택에 쌓고,
// metadata 도착 시(=그때까지 쌓인 tool_start가 전부 성공했다는 뜻) 완료형으로 일괄 교체한다.
// 새 tool을 추가할 때 이 두 맵에도 함께 등록해야 문구가 제대로 노출된다.
public final class ToolProgressMessages {

    private static final Map<String, String> IN_PROGRESS_MESSAGES = Map.ofEntries(
        Map.entry("search_projects", "오목눈이가 프로젝트를 검색하는 중..."),
        Map.entry("browse_projects", "오목눈이가 프로젝트를 둘러보는 중..."),
        Map.entry("list_project_categories", "오목눈이가 카테고리를 확인하는 중..."),
        Map.entry("get_project_detail", "오목눈이가 프로젝트 정보를 확인하는 중..."),
        Map.entry("get_project_rewards", "오목눈이가 리워드 목록을 확인하는 중..."),
        Map.entry("get_reward_detail", "오목눈이가 리워드 정보를 확인하는 중..."),
        Map.entry("search_reviews", "오목눈이가 리뷰를 살펴보는 중..."),
        Map.entry("search_policy", "오목눈이가 정책을 확인하는 중...")
    );

    private static final Map<String, String> COMPLETED_MESSAGES = Map.ofEntries(
        Map.entry("search_projects", "오목눈이가 프로젝트를 검색했어요!"),
        Map.entry("browse_projects", "오목눈이가 프로젝트를 둘러봤어요!"),
        Map.entry("list_project_categories", "오목눈이가 카테고리를 확인했어요!"),
        Map.entry("get_project_detail", "오목눈이가 프로젝트 정보를 확인했어요!"),
        Map.entry("get_project_rewards", "오목눈이가 리워드 목록을 확인했어요!"),
        Map.entry("get_reward_detail", "오목눈이가 리워드 정보를 확인했어요!"),
        Map.entry("search_reviews", "오목눈이가 리뷰를 살펴봤어요!"),
        Map.entry("search_policy", "오목눈이가 정책을 확인했어요!")
    );

    private static final String DEFAULT_IN_PROGRESS_MESSAGE = "오목눈이가 확인하는 중...";
    private static final String DEFAULT_COMPLETED_MESSAGE = "오목눈이가 확인했어요!";

    private ToolProgressMessages() {
    }

    public static String messageFor(String toolName) {
        return IN_PROGRESS_MESSAGES.getOrDefault(toolName, DEFAULT_IN_PROGRESS_MESSAGE);
    }

    public static String completedMessageFor(String toolName) {
        return COMPLETED_MESSAGES.getOrDefault(toolName, DEFAULT_COMPLETED_MESSAGE);
    }
}
