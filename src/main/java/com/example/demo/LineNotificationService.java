package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class LineNotificationService {
    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private final RestClient restClient = RestClient.create();

    public void send(String accessToken, String userId, String text) {
        if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("LINE Access TokenとUser IDを入力してください。");
        }
        Map<String, Object> body = Map.of(
                "to", userId.trim(),
                "messages", List.of(Map.of("type", "text", "text", text)));
        restClient.post()
                .uri(PUSH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken.trim())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
