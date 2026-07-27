package com.growmighty.lectures.firstday.settlement.application;

import java.util.List;

public final class LegacyProjectSettlementOriginalMigrationException extends IllegalStateException {

    private final List<String> failures;

    public LegacyProjectSettlementOriginalMigrationException(List<String> failures) {
        super("프로젝트 정산 원본 백필 실패: " + String.join(", ", failures));
        this.failures = List.copyOf(failures);
    }

    public List<String> failures() {
        return failures;
    }
}
