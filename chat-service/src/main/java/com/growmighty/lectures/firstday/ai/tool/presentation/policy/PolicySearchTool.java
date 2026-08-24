package com.growmighty.lectures.firstday.ai.tool.presentation.policy;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicySearchPort;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PolicySearchTool {

    private final PolicySearchPort policySearchPort;
    private final ToolInvocationRecorder recorder;

    @Tool(name = "search_policy", description = "얼리버드 서비스 정책에 대한 질문에 답하기 위해 정책 문서를 검색한다.")
    public List<PolicyChunkResult> searchPolicy(
        @ToolParam(description = "사용자의 정책 관련 질문(자연어 그대로)")
        String query,
        @ToolParam(description =
            "질문이 명확히 특정 도메인에 해당하면 지정. 애매하거나 여러 도메인에 걸치면 생략.\n" +
            "USER: 회원가입/로그인/계정 관리 등 유저 정책\n" +
            "PROJECT: 프로젝트 등록/운영 정책\n" +
            "BOARD: 리뷰/공지/문의 작성 및 운영 정책\n" +
            "ORDER: 장바구니/주문/리워드 선택 절차 (order-service+cart-service)\n" +
            "PAYMENT: 결제 수단, 결제 실패/취소 처리\n" +
            "SETTLEMENT: 펀딩 성공 시 창작자 정산, 실패 시 백커 일괄환불\n" +
            "GENERAL: 얼리버드가 어떤 플랫폼인지, All-or-Nothing 펀딩 방식, 수수료 정책 등 특정 도메인 서비스에 속하지 않는 일반 소개", required = false)
        PolicyCategory category
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query는 비어 있을 수 없습니다.");
        }
        List<PolicyChunkResult> results = policySearchPort.search(query, category);
        recorder.recordToolUsed("search_policy");
        recorder.recordPolicyReferences(results);
        return results;
    }
}
