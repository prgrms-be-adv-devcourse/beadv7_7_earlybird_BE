package com.growmighty.lectures.firstday.ai.chat.presentation;

import com.growmighty.lectures.firstday.ai.chat.application.ChatOrchestrationService;
import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ChatMessageRequest;
import com.growmighty.lectures.firstday.ai.conversation.application.ConversationIdentityResolver;
import com.growmighty.lectures.firstday.ai.conversation.domain.ConversationIdentity;
import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ConversationHistoryStore;
import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ShownProjectStore;
import com.growmighty.lectures.firstday.ai.conversation.presentation.AnonIdCookieWriter;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationIdentityResolver identityResolver;
    private final ConversationHistoryStore historyStore;
    private final ShownProjectStore shownProjectStore;
    private final AnonIdCookieWriter cookieWriter;
    private final ChatOrchestrationService chatOrchestrationService;

    @PostMapping(value = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
        @RequestHeader(value = JwtHeaders.USER_ID, required = false) Long userId,
        @CookieValue(value = AnonIdCookieWriter.COOKIE_NAME, required = false) String anonId,
        @Valid @RequestBody ChatMessageRequest request,
        HttpServletResponse response
    ) {
        ConversationIdentity identity = identityResolver.resolve(userId, anonId);
        if(identity.issuedAnonId() != null) {
            cookieWriter.write(response, identity.issuedAnonId());
        }
        return chatOrchestrationService.sendMessage(identity.key(), request.message());
    }

    @PostMapping("/sessions/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resetSession(
        @RequestHeader(value = JwtHeaders.USER_ID, required = false) Long userId,
        @CookieValue(value = AnonIdCookieWriter.COOKIE_NAME, required = false) String anonId
    ) {
        ConversationIdentity identity = identityResolver.resolve(userId, anonId);
        historyStore.clear(identity.key());
        shownProjectStore.clear(identity.key());
    }
}
