package com.growmighty.lectures.firstday.ai.conversation.presentation;

import com.growmighty.lectures.firstday.ai.conversation.application.ConversationIdentityResolver;
import com.growmighty.lectures.firstday.ai.conversation.domain.ConversationIdentity;
import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ConversationHistoryStore;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.HttpStatus;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *  임시 컨트롤러 - 브랜치 종단 검증 전용.
 *  브랜치 4가 실제 채팅 엔드포인트를 만들면 이 클래스는 삭제한다.
 */
@RestController
@RequestMapping("/internal/conversation-test")
@RequiredArgsConstructor
public class ConversationVerificationController {

    private final ConversationIdentityResolver identityResolver;
    private final ConversationHistoryStore historyStore;
    private final AnonIdCookieWriter cookieWriter;

    @PostMapping
    public ConversationTestResponse echo(
        @RequestHeader(value = JwtHeaders.USER_ID, required = false) Long userId,
        @CookieValue(value = AnonIdCookieWriter.COOKIE_NAME, required = false) String anonId,
        @RequestBody ConversationTestRequest request,
        HttpServletResponse response
    ) {
        ConversationIdentity identity = identityResolver.resolve(userId, anonId);
        if (identity.issuedAnonId() != null) {
            cookieWriter.write(response, identity.issuedAnonId());
        }
        historyStore.append(identity.key(), new UserMessage(request.message()));
        List<Message> history = historyStore.get(identity.key());
        return new ConversationTestResponse(identity.key(), history.size());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reset(
        @RequestHeader(value = JwtHeaders.USER_ID, required = false) Long userId,
        @CookieValue(value = AnonIdCookieWriter.COOKIE_NAME, required = false) String anonId
    ) {
        ConversationIdentity identity = identityResolver.resolve(userId, anonId);
        historyStore.evict(identity.key());
    }

    record ConversationTestRequest(String message) {}

    record ConversationTestResponse(String key, int historySize) {}
}
