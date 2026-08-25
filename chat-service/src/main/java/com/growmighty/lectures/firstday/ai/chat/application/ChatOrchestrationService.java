package com.growmighty.lectures.firstday.ai.chat.application;

import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ChatStreamMetadata;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {
    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final ChatClient chatClient;

    public SseEmitter sendMessage(String conversationId, String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        ToolInvocationRecorder recorder = new ToolInvocationRecorder(emitter, conversationId);
        AtomicBoolean metadataSent = new AtomicBoolean(false);

        chatClient.prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .toolContext(Map.of(ToolInvocationRecorder.TOOL_CONTEXT_KEY, recorder))
            .user(message)
            .stream()
            .content()
            .subscribe(
                chunk -> emitChunk(recorder, metadataSent, chunk),
                emitter::completeWithError,
                emitter::complete
            );

        return emitter;
    }

    private void emitChunk(ToolInvocationRecorder recorder, AtomicBoolean metadataSent, String chunk) {
        if (metadataSent.compareAndSet(false, true)) {
            recorder.send(SseEmitter.event()
                .name("metadata")
                .data(ChatStreamMetadata.of(recorder.toolsUsed(), recorder.policyReferences(), recorder.projects())));
        }
        recorder.send(SseEmitter.event().name("chunk").data(chunk));
    }
}
