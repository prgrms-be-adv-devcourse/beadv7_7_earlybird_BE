package com.growmighty.lectures.firstday.ai.chat.application;

import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ChatMessageResponse;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {
    private final ChatClient chatClient;
    private final ToolInvocationRecorder recorder;

    public ChatMessageResponse sendMessage(String conversationId, String message) {
        String reply = chatClient.prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(message)
            .call()
            .content();

        return ChatMessageResponse.of(reply, recorder.toolsUsed(), recorder.policyReferences());
    }
}
