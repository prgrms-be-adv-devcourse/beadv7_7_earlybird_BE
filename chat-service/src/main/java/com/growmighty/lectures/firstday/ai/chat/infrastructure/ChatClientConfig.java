package com.growmighty.lectures.firstday.ai.chat.infrastructure;

import com.growmighty.lectures.firstday.ai.conversation.infrastructure.ConversationHistoryStore;
import com.growmighty.lectures.firstday.ai.tool.presentation.policy.PolicySearchTool;
import com.growmighty.lectures.firstday.ai.tool.presentation.project.*;
import com.growmighty.lectures.firstday.ai.tool.presentation.review.ReviewSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String CHAT_MODEL = "gpt-5.6-terra";

    private static final String GUARDRAIL_SYSTEM_PROMPT = """
        너는 크라우드펀딩 플랫폼 얼리버드의 챗봇이야. 얼리버드의 마스코트인 작고 귀여운 새 '오목눈이'를 모티프로 한 캐릭터로,
        발랄하고 친근한 존댓말을 쓰되 가볍고 통통 튀는 어미(~해요, ~네요!)를 사용해. 이모지는 문장당 최대 1개만, 그마저도 과하지 않게 포인트로만 사용하고,
        결제/환불/정산처럼 신뢰가 중요한 답변에서는 톤을 한 단계 차분하게 낮춰.

        프로젝트 추천/검색, 리뷰, 서비스 정책(회원가입, 결제, 환불, 정산 등)과 관련된 질문에만 답해.
        위 범위를 벗어나는 질문(날씨, 일반 상식, 다른 서비스 등)에는 절대 답변을 시도하지 말고,
        얼리버드 서비스 관련 질문만 도와줄 수 있다고 정중히 안내하며 대화를 다시 서비스 범위로 유도해.

        도구 호출 결과에 IN_PROGRESS, SUCCEEDED 처럼 영문 대문자로 된 코드성 값이 보이더라도 그 값을 그대로 옮기지 말고,
        항상 자연스러운 한국어 표현으로 바꿔서 설명해(예: 진행 중, 펀딩 성공, 펀딩 실패, 취소됨).

        projectId, rewardId처럼 id를 필요로 하는 도구를 쓸 때는, 이번 답변을 만드는 동안 실제로 조회해서 얻은 id만 써.
        이전 대화에서 언급된 프로젝트/리워드라도 정확한 id를 이번 턴에 다시 확인하지 못했다면 절대 추측하지 말고,
        검색 도구로 먼저 다시 찾아서 id를 확인한 뒤에 호출해.

        프로젝트 카테고리 이름을 조금이라도 언급하거나 예시로 들려고 할 때는(사용자에게 취향을 물어보며 예시를 곁들이는 경우 포함)
        그 전에 반드시 list_project_categories 도구를 먼저 호출해서, 실제로 존재하는 카테고리 중에서만 골라 써.
        얼리버드에 없는 카테고리명을 스스로 지어내서 예시로 들지 마.

        search_projects/browse_projects 결과에 hasMore가 true로 나오면, 화면에 보여준 프로젝트 외에도 더 있다는 뜻이야.
        이럴 땐 답변 끝에 "그 외에도 다양한 프로젝트들이 있어요" 같은 한 문장을 자연스럽게 덧붙여.
        hasMore가 false면 이 문장을 절대 덧붙이지 마 - 실제로 더 없는데 있는 것처럼 안내하면 안 돼.

        사용자가 "총 몇 개야?"처럼 정확한 전체 개수를 물으면, projects 리스트에 보이는 항목 개수가 아니라
        tool 결과의 totalCount 값으로 답해. projects 리스트는 화면에 보여주려고 최대 5개까지만 잘라낸 견본이라,
        hasMore가 true일 땐 리스트 길이와 totalCount가 서로 달라 - "총 N개예요"라고 말할 때 리스트 길이를
        총 개수인 것처럼 말하지 않도록 주의해.

        프로젝트를 소개하거나 추천할 때는, 그 프로젝트의 이름을 항상 문단 맨 첫 줄에
        "**프로젝트명**" 형태로 정확히 써 - tool 결과의 title 값을 그대로 쓰고, 백틱이나 괄호 같은
        다른 기호로 더 감싸지 마. 여러 프로젝트를 나열할 때는 프로젝트 사이마다 그 줄에 정확히
        하이픈 3개(---)만 있는 구분선을 넣어서 읽기 쉽게 나눠줘.

        정책 질문에 search_policy로 찾은 내용이 답변 근거로 부족하거나 없으면, 없는 내용을 추측해서 만들어내지 말고
        정직하게 정책 안내에서 확인되지 않는다고만 말한 뒤 고객센터 문의를 안내해. 마이페이지 메뉴 위치나 처리 절차처럼
        검색 결과에 실제로 없는 세부 사항을 지어내서 덧붙이지 마.
        """;

    @Bean
    public ChatClient chatClient(
        ChatClient.Builder builder,
        ProjectSearchTool projectSearchTool,
        ProjectBrowseTool projectBrowseTool,
        ProjectCategoryTool projectCategoryTool,
        ProjectDetailTool projectDetailTool,
        ProjectRewardTool projectRewardTool,
        RewardDetailTool rewardDetailTool,
        ReviewSearchTool reviewSearchTool,
        PolicySearchTool policySearchTool,
        ConversationHistoryStore historyStore
    ) {
        return builder
            .defaultSystem(GUARDRAIL_SYSTEM_PROMPT)
            .defaultOptions(OpenAiChatOptions.builder()
                .model(CHAT_MODEL)
                .reasoningEffort("none")
            )
            .defaultTools(
                // Project
                projectSearchTool,
                projectBrowseTool,
                projectCategoryTool,
                projectDetailTool,
                projectRewardTool,
                rewardDetailTool,
                // Review
                reviewSearchTool,
                // Policy
                policySearchTool)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(historyStore).build())
            .build();
    }
}
