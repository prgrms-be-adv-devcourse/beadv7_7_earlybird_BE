package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

public class ProjectStatusDisplay {

    private ProjectStatusDisplay() {
    }
    public static String toKorean(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "IN_PROGRESS" -> "진행 중";
            case "SUCCEEDED" -> "펀딩 성공";
            case "FAILED" -> "펀딩 실패";
            case "CANCELLED" -> "취소됨";
            default -> status;
        };
    }
}
