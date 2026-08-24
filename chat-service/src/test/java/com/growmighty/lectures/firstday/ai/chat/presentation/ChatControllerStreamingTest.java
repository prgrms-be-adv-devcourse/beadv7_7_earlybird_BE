package com.growmighty.lectures.firstday.ai.chat.presentation;

import com.growmighty.lectures.firstday.ai.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ChatControllerStreamingTest.StubChatModelConfig.class)
class ChatControllerStreamingTest {

    @LocalServerPort
    int port;

    @Test
    void 툴호출없는_응답은_metadata_다음에_chunk가_순서대로_온다() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/chat/messages"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"안녕\"}"))
            .build();

        HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
        List<SseEvent> events = parseEvents(response.body().toList());

        assertThat(events.get(0).name()).isEqualTo("metadata");
        assertThat(events.get(0).data()).contains("\"toolsUsed\":[]");

        String assembledText = events.stream()
            .filter(e -> e.name().equals("chunk"))
            .map(SseEvent::data)
            .reduce("", String::concat);
        assertThat(assembledText).isEqualTo("안녕하세요!");

        assertThat(events).noneMatch(e -> e.name().equals("tool_start"));
    }

    private List<SseEvent> parseEvents(List<String> lines) {
        List<SseEvent> events = new ArrayList<>();
        String pendingName = null;
        for (String line : lines) {
            if (line.startsWith("event:")) {
                pendingName = line.substring("event:".length());
            } else if (line.startsWith("data:") && pendingName != null) {
                events.add(new SseEvent(pendingName, line.substring("data:".length())));
                pendingName = null;
            }
        }
        return events;
    }

    private record SseEvent(String name, String data) {
    }

    @TestConfiguration
    static class StubChatModelConfig {
        @Bean
        @Primary
        ChatModel chatModel() {
            return new StubChatModel(List.of("안녕", "하세요", "!"));
        }
    }
}
