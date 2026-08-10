// TODO(settlement-plan): Delete synchronous Project reader tests after event projection tests cover the interface.
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
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import java.io.IOException;
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
    private ProjectOutcomeReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new ProjectSettlementTargetHttpReader(builder.build());
    }

    @Test
    @DisplayName("세 상태의 ProjectResponse에서 결과 상태와 최소 식별자를 보존한다")
    void readsProjectOutcomesForAllStatuses() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "thumbnailId": 301,
                              "title": "Settlement가 소비하지 않는 프로젝트 제목",
                              "categoryId": 401,
                              "summary": "요약",
                              "description": "설명",
                              "goalAmount": 100000,
                              "fundedAmount": 120000,
                              "startAt": "2026-06-01T09:00:00",
                              "endAt": "2026-07-31",
                              "status": "SUCCEEDED",
                              "closed": true,
                              "rejectReason": null,
                              "submittedAt": "2026-05-20T09:00:00",
                              "approvedAt": "2026-05-21T09:00:00",
                              "closedAt": "2026-08-01T00:00:01",
                              "createdAt": "2026-05-20T08:00:00",
                              "updatedAt": "2026-08-01T00:00:01"
                            },
                            {
                              "projectId": 102,
                              "creatorId": 202,
                              "status": "SUCCEEDED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=FAILED"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 103,
                              "creatorId": 203,
                              "status": "FAILED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=CANCELLED"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 104,
                              "creatorId": 204,
                              "status": "CANCELLED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProjectOutcome> outcomes = reader.findProjectOutcomes();

        assertThat(outcomes).containsExactlyInAnyOrder(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(102L, 202L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(103L, 203L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(104L, 204L, ProjectOutcomeStatus.CANCELLED)
        );
        server.verify();
    }

    @Test
    @DisplayName("세 상태의 Project 결과가 없으면 빈 목록을 반환한다")
    void returnsEmptyTargets() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=FAILED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=CANCELLED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(reader.findProjectOutcomes()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Project 실패 봉투를 정산 대상 조회 불가로 번역한다")
    void rejectsFailureEnvelope() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
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

        assertThatThrownBy(reader::findProjectOutcomes)
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
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
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

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("필수 필드가 누락된 정산 대상을 조회 불가로 번역한다")
    void rejectsTargetMissingRequiredField() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "status": "SUCCEEDED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("SUCCEEDED가 아닌 Project 응답을 조회 계약 위반으로 거부한다")
    void rejectsTargetWithUnexpectedStatus() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "FAILED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("중복 프로젝트 식별자를 조회 불가로 번역한다")
    void rejectsDuplicateProjectId() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "SUCCEEDED"
                            },
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "SUCCEEDED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("서로 다른 상태 응답의 프로젝트 식별자가 중복되면 전체 결과를 거부한다")
    void rejectsDuplicateProjectIdAcrossStatuses() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "SUCCEEDED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=FAILED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "FAILED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=CANCELLED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("앞선 상태 조회가 성공해도 뒤 상태의 HTTP 실패를 부분 결과로 반환하지 않는다")
    void rejectsPartialResultsWhenLaterStatusFails() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "projectId": 101,
                              "creatorId": 201,
                              "status": "SUCCEEDED"
                            }
                          ],
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=FAILED"
                ))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
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

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    @DisplayName("Project HTTP 실패를 정산 대상 조회 불가로 번역한다")
    void translatesProjectHttpFailure(int statusCode) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
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

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 Project 응답을 정산 대상 조회 불가로 번역한다")
    void translatesMalformedResponse() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertUnavailable(reader::findProjectOutcomes);
        server.verify();
    }

    @Test
    @DisplayName("Project 연결 실패를 정산 대상 조회 불가로 번역한다")
    void translatesConnectionFailure() {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/v1/projects?status=SUCCEEDED"
                ))
                .andRespond(request -> {
                    throw new IOException("Project 연결 원문 오류");
                });

        assertUnavailable(reader::findProjectOutcomes);
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
