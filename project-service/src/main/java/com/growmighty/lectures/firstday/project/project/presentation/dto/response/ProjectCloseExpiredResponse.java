package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import java.util.List;

public record ProjectCloseExpiredResponse(
        int processedCount,
        List<Long> closedProjectIds,
        List<Long> failedProjectIds
) {
}
