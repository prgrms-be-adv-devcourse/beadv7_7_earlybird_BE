package com.growmighty.lectures.firstday.ai.conversation.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConversationHistoryStore {
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_HISTORY_SIZE = 200;

    private final Cache<String, List<Message>> cache = Caffeine
        .newBuilder()
        .expireAfterAccess(IDLE_TIMEOUT)
        .build();

    public List<Message> get(String key) {
        List<Message> history = cache.getIfPresent(key);
        if (history == null) {
            return List.of();
        }
        int size = history.size();
        if(size <= MAX_HISTORY_SIZE) {
            return List.copyOf(history);
        }
        return List.copyOf(history.subList(size - MAX_HISTORY_SIZE, size));
    }

    public void append(String key, Message message) {
        cache.asMap()
            .computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
            .add(message);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

}
