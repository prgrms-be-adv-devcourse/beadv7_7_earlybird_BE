package com.growmighty.lectures.firstday.ai.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 진짜 OpenAI 호출 없이 ChatClient/tool-calling advisor 이하를 전부 실제로 태우기 위한 테스트 전용 대역.
 * ChatModel 인터페이스 경계에서만 응답을 고정값으로 대체한다.
 */
public class StubChatModel implements ChatModel {

    private final List<String> chunks;

    public StubChatModel(List<String> chunks) {
        this.chunks = chunks;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(String.join("", chunks)))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.fromIterable(chunks)
            .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))));
    }
}
