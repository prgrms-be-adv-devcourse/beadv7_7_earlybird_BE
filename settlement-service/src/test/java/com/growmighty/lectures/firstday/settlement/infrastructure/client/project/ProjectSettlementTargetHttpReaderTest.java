package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProjectSettlementTargetHttpReaderTest {

    private static final String BASE_URL = "http://project-service";

    private MockRestServiceServer server;
    private ProjectSettlementTargetReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new ProjectSettlementTargetHttpReader(builder.build());
    }

    @Test
    @DisplayName("정산 월에 완료되고 펀딩에 성공한 프로젝트 정산 대상을 조회한다")
    void readsProjectSettlementTargets() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "projectTitle": "여름의 기록",
                              "creatorId": 201
                            },
                            {
                              "projectId": 102,
                              "projectTitle": "작은 숲",
                              "creatorId": 202
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProjectSettlementTarget> targets = reader.findSettlementTargets(
                YearMonth.of(2026, 7)
        );

        assertThat(targets).containsExactly(
                new ProjectSettlementTarget(101L, "여름의 기록", 201L),
                new ProjectSettlementTarget(102L, "작은 숲", 202L)
        );
        server.verify();
    }

    @Test
    @DisplayName("정산 대상이 없으면 빈 목록을 반환한다")
    void returnsEmptyTargets() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(reader.findSettlementTargets(YearMonth.of(2026, 7))).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Project 실패 봉투를 정산 대상 조회 불가로 번역한다")
    void rejectsFailureEnvelope() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "message": "Project 내부 오류"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)))
                .isInstanceOfSatisfying(SettlementException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(
                            SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE
                    );
                    assertThat(exception.getMessage())
                            .doesNotContain("Project 내부 오류");
                });
        server.verify();
    }

    @Test
    @DisplayName("성공 데이터와 오류가 함께 있는 모순된 봉투를 거부한다")
    void rejectsSuccessEnvelopeContainingError() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": {
                            "message": "Project 원문 내부 오류"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    @Test
    @DisplayName("필수 필드가 누락된 정산 대상을 조회 불가로 번역한다")
    void rejectsTargetMissingRequiredField() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    @Test
    @DisplayName("중복 프로젝트 식별자를 조회 불가로 번역한다")
    void rejectsDuplicateProjectId() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "projectTitle": "여름의 기록",
                              "creatorId": 201
                            },
                            {
                              "projectId": 101,
                              "projectTitle": "중복된 기록",
                              "creatorId": 201
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    @DisplayName("Project HTTP 실패를 정산 대상 조회 불가로 번역한다")
    void translatesProjectHttpFailure(int statusCode) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "success": false,
                                  "data": null,
                                  "error": {
                                    "message": "Project 원문 내부 오류"
                                  }
                                }
                                """));

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 Project 응답을 정산 대상 조회 불가로 번역한다")
    void translatesMalformedResponse() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    @Test
    @DisplayName("Project 연결 실패를 정산 대상 조회 불가로 번역한다")
    void translatesConnectionFailure() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/projects/settlement-targets?settlementMonth=2026-07"
                ))
                .andRespond(request -> {
                    throw new IOException("Project 연결 원문 오류");
                });

        assertUnavailable(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)));
        server.verify();
    }

    private static void assertUnavailable(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(SettlementException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(
                            SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE
                    );
                    assertThat(exception.getMessage())
                            .doesNotContain("Project 원문 내부 오류");
                });
    }
}
