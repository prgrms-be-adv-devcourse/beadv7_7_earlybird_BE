package com.growmighty.lectures.firstday.ai.chat.application;

import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ShownProjectStore;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {
    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final ChatClient chatClient;
    private final ShownProjectStore shownProjectStore;

    public SseEmitter sendMessage(String conversationId, String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        ToolInvocationRecorder recorder = new ToolInvocationRecorder(emitter, conversationId);
        StringBuilder fullText = new StringBuilder();

        Disposable subscription = chatClient.prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .toolContext(Map.of(ToolInvocationRecorder.TOOL_CONTEXT_KEY, recorder))
            .user(message)
            .stream()
            .content()
            .subscribe(
                chunk -> emitChunk(recorder, fullText, chunk),
                emitter::completeWithError,
                () -> completeStream(recorder, fullText, emitter)
            );

        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(e -> subscription.dispose());

        return emitter;
    }

    private void emitChunk(ToolInvocationRecorder recorder, StringBuilder fullText, String chunk) {
        recorder.ensureMetadataSent();
        fullText.append(chunk);
        recorder.send(SseEmitter.event().name("chunk").data(chunk));
    }

    private void completeStream(ToolInvocationRecorder recorder, StringBuilder fullText, SseEmitter emitter) {
        List<Long> narratedIds = recorder.narratedBrowseCandidateIds(fullText.toString());
        if (!narratedIds.isEmpty()) {
            shownProjectStore.addShown(recorder.conversationId(), narratedIds);
        }
        emitter.complete();
    }
}
