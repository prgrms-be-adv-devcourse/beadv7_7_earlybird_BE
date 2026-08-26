package com.growmighty.lectures.firstday.ai.policy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyReindexStartupRunner {

    private final PolicyReindexService reindexService;

    @EventListener(ApplicationReadyEvent.class)
    public void reindexOnStartup() {
        log.info("서비스 기동 - 정책 문서 자동 재색인 시작");
        reindexService.reindexAll();
    }
}
