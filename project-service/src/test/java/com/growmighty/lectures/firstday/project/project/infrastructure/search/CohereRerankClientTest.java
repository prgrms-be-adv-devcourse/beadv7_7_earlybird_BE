package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CohereRerankClientTest {

    private MockRestServiceServer server;
    private CohereRerankClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.cohere.com")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        CohereRerankProperties props =
                new CohereRerankProperties(true, "https://api.cohere.com", "rerank-v3.5", 40, "test-key", 3000);
        client = new CohereRerankClient(builder.build(), props);
    }

    @Test
    void sendsQueryAndDocumentsAndParsesRankedResults() {
        server.expect(requestTo("https://api.cohere.com/v2/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query").value("강아지 옷"))
                .andExpect(jsonPath("$.model").value("rerank-v3.5"))
                .andExpect(jsonPath("$.top_n").value(2))
                .andExpect(jsonPath("$.documents[0]").value("A"))
                .andRespond(withSuccess(
                        "{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.2}]}",
                        MediaType.APPLICATION_JSON));

        List<CohereRerankClient.Ranked> ranked = client.rerank("강아지 옷", List.of("A", "B"));

        assertThat(ranked).extracting(CohereRerankClient.Ranked::index).containsExactly(1, 0);
        assertThat(ranked.get(0).relevanceScore()).isEqualTo(0.9);
        server.verify();
    }

    @Test
    void propagatesServerError() {
        server.expect(requestTo("https://api.cohere.com/v2/rerank")).andRespond(withServerError());

        assertThatThrownBy(() -> client.rerank("q", List.of("A")))
                .isInstanceOf(RestClientException.class);
    }
}
