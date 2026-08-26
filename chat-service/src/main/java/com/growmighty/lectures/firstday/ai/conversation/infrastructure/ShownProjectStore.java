package com.growmighty.lectures.firstday.ai.conversation.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// browse_projects가 한 대화 안에서 이미 보여준 projectId를 기억해뒀다가 다음 호출에서 자동 제외한다 -
// project-service에 페이지네이션이 없어 같은 조건으로 다시 불러도 항상 동일한 상위 N개만 반환되는
// 문제 대응. ConversationHistoryStore와 동일한 생명주기 (30분 idle) 로 관리.
@Component
public class ShownProjectStore {
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Cache<String, Set<Long>> cache = Caffeine
        .newBuilder()
        .expireAfterAccess(IDLE_TIMEOUT)
        .build();

    public Set<Long> get(String conversationId) {
        Set<Long> shown = cache.getIfPresent(conversationId);
        return shown == null ? Set.of() : Set.copyOf(shown);
    }

    public void addShown(String conversationId, List<Long> projectIds) {
        cache.asMap()
            .computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet())
            .addAll(projectIds);
    }

    public void clear(String conversationId) {
        cache.invalidate(conversationId);
    }
}
