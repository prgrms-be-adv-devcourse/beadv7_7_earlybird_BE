package com.growmighty.lectures.firstday.ai.conversation.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConversationHistoryStore implements ChatMemory {
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_HISTORY_SIZE = 200;

    private final Cache<String, List<Message>> cache = Caffeine
        .newBuilder()
        .expireAfterAccess(IDLE_TIMEOUT)
        .build();

    @Override
    public List<Message> get(String conversationId) {
        List<Message> history = cache.getIfPresent(conversationId);
        if (history == null) {
            return List.of();
        }
        int size = history.size();
        if(size <= MAX_HISTORY_SIZE) {
            return List.copyOf(history);
        }
        return List.copyOf(history.subList(size - MAX_HISTORY_SIZE, size));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        cache.asMap()
            .computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>())
            .addAll(messages);
    }

    @Override
    public void clear(String conversationId) {
        cache.invalidate(conversationId);
    }

}
