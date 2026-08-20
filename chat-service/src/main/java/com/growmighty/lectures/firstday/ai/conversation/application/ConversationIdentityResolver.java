package com.growmighty.lectures.firstday.ai.conversation.application;

import com.growmighty.lectures.firstday.ai.conversation.domain.ConversationIdentity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConversationIdentityResolver {

    private static final String USER_KEY_PREFIX = "user:";
    private static final String ANON_KEY_PREFIX = "anon:";

    public ConversationIdentity resolve(Long userId, String anonId) {
        if (userId != null) {
            return new ConversationIdentity(USER_KEY_PREFIX + userId, null);
        }
        String resolvedAnonId = anonId != null ? anonId : UUID.randomUUID().toString();
        String issuedAnonId = anonId == null ? resolvedAnonId : null;
        return new ConversationIdentity(ANON_KEY_PREFIX + resolvedAnonId, issuedAnonId);
    }

}
