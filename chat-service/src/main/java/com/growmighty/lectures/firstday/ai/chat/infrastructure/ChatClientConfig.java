package com.growmighty.lectures.firstday.ai.chat.infrastructure;

import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ConversationHistoryStore;
import com.growmighty.lectures.firstday.ai.tool.presentation.PolicySearchTool;
import com.growmighty.lectures.firstday.ai.tool.presentation.ProjectSearchTool;
import com.growmighty.lectures.firstday.ai.tool.presentation.ReviewSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String GUARDRAIL_SYSTEM_PROMPT = """
        너는 크라우드펀딩 플랫폼 얼리버드의 챗봇이야. 얼리버드의 마스코트인 작고 귀여운 새 '오목눈이'를 모티프로 한 캐릭터로,
        발랄하고 친근한 존댓말을 쓰되 가볍고 통통 튀는 어미(~해요, ~네요!)를 사용해. 이모지는 문장당 최대 1개만, 그마저도 과하지 않게 포인트로만 사용하고,
        결제/환불/정산처럼 신뢰가 중요한 답변에서는 톤을 한 단계 차분하게 낮춰.

        프로젝트 추천/검색, 리뷰, 서비스 정책(회원가입, 결제, 환불, 정산 등)과 관련된 질문에만 답해.
        위 범위를 벗어나는 질문(날씨, 일반 상식, 다른 서비스 등)에는 절대 답변을 시도하지 말고,
        얼리버드 서비스 관련 질문만 도와줄 수 있다고 정중히 안내하며 대화를 다시 서비스 범위로 유도해.
        """;

    @Bean
    public ChatClient chatClient(
        ChatClient.Builder builder,
        ProjectSearchTool projectSearchTool,
        ReviewSearchTool reviewSearchTool,
        PolicySearchTool policySearchTool,
        ConversationHistoryStore historyStore
    ) {
        return builder
            .defaultSystem(GUARDRAIL_SYSTEM_PROMPT)
            .defaultTools(projectSearchTool, reviewSearchTool, policySearchTool)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(historyStore).build())
            .build();
    }
}
