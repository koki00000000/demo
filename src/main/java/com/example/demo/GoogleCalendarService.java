package com.example.demo;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GoogleCalendarService {
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private final RestClient restClient = RestClient.create();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.calendar.client-id:}")
    private String clientId;
    @Value("${google.calendar.client-secret:}")
    private String clientSecret;
    @Value("${google.calendar.redirect-uri:http://localhost:8080/api/google-calendar/callback}")
    private String redirectUri;

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String authorizationUrl(String state) {
        requireConfigured();
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPE)
                + "&access_type=offline&prompt=consent&include_granted_scopes=true"
                + "&state=" + encode(state);
    }

    public String exchangeCode(String code) {
        LinkedMultiValueMap<String, String> form = tokenForm();
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        JsonNode response = postToken(form);
        String refreshToken = response.path("refresh_token").asText();
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("Googleから更新用トークンを取得できませんでした。もう一度接続してください。");
        }
        return refreshToken;
    }

    public SyncResult sync(Schedule schedule, String refreshToken) {
        String accessToken = accessToken(refreshToken);
        return syncWithAccessToken(schedule, accessToken);
    }

    public Map<Long, SyncResult> syncAll(List<Schedule> schedules, String refreshToken) {
        String accessToken = accessToken(refreshToken);
        Map<Long, SyncResult> results = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            results.put(schedule.getId(), syncWithAccessToken(schedule, accessToken));
        }
        return results;
    }

    public SyncResult sync(Todo todo, String refreshToken) {
        return syncTodoWithAccessToken(todo, accessToken(refreshToken));
    }

    public Map<Long, SyncResult> syncAllTodos(List<Todo> todos, String refreshToken) {
        String accessToken = accessToken(refreshToken);
        Map<Long, SyncResult> results = new LinkedHashMap<>();
        for (Todo todo : todos) {
            results.put(todo.getId(), syncTodoWithAccessToken(todo, accessToken));
        }
        return results;
    }

    private SyncResult syncWithAccessToken(Schedule schedule, String accessToken) {
        String eventId = schedule.getGoogleCalendarEventId();
        boolean alreadySynced = eventId != null && !eventId.isBlank();
        if (!alreadySynced) {
            eventId = "ais" + schedule.getUserId() + "e" + schedule.getId();
        }
        Map<String, Object> event = eventBody(schedule, eventId);
        String eventUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + encode(eventId);
        JsonNode result;
        if (alreadySynced) {
            try {
                result = updateEvent(eventUrl, event, accessToken);
            } catch (HttpClientErrorException.NotFound notFound) {
                result = insertEvent(event, accessToken);
            }
        } else {
            try {
                result = insertEvent(event, accessToken);
            } catch (HttpClientErrorException.Conflict conflict) {
                // 同じIDの予定がGoogle側に残っている場合は重複を作らず更新する。
                result = updateEvent(eventUrl, event, accessToken);
            }
        }
        return new SyncResult(result.path("id").asText(eventId), result.path("htmlLink").asText());
    }

    private SyncResult syncTodoWithAccessToken(Todo todo, String accessToken) {
        LocalDate date = todo.getScheduledDate();
        if (date == null) {
            throw new IllegalArgumentException("日付がないTodoは送信できません（Todo ID: " + todo.getId() + "）。");
        }
        String eventId = "ait" + todo.getUserId() + "e" + todo.getId();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("summary", "Todo: " + todo.getTitle());
        event.put("description", "AI予定秘書のTodo（Todo ID: " + todo.getId() + "）");
        event.put("start", Map.of("date", date.toString()));
        event.put("end", Map.of("date", date.plusDays(1).toString()));
        event.put("transparency", "transparent");
        event.put("reminders", Map.of("useDefault", false));
        JsonNode result;
        try {
            result = insertEvent(event, accessToken);
        } catch (HttpClientErrorException.Conflict conflict) {
            String eventUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + encode(eventId);
            result = updateEvent(eventUrl, event, accessToken);
        }
        return new SyncResult(result.path("id").asText(eventId), result.path("htmlLink").asText());
    }

    private JsonNode insertEvent(Map<String, Object> event, String accessToken) {
        return restClient.post()
                .uri("https://www.googleapis.com/calendar/v3/calendars/primary/events?sendUpdates=none")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).body(event).retrieve().body(JsonNode.class);
    }

    private JsonNode updateEvent(String eventUrl, Map<String, Object> event, String accessToken) {
        return restClient.put().uri(eventUrl)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).body(event).retrieve().body(JsonNode.class);
    }

    private String accessToken(String refreshToken) {
        LinkedMultiValueMap<String, String> form = tokenForm();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        String token = postToken(form).path("access_token").asText();
        if (token.isBlank()) throw new IllegalStateException("Googleの認証を更新できませんでした。再接続してください。");
        return token;
    }

    private LinkedMultiValueMap<String, String> tokenForm() {
        requireConfigured();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private JsonNode postToken(LinkedMultiValueMap<String, String> form) {
        String body = form.toSingleValueMap().entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google OAuth error: " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google OAuthへの接続が中断されました。", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Google OAuthへ接続できませんでした。", exception);
        }
    }

    private Map<String, Object> eventBody(Schedule schedule, String eventId) {
        LocalDateTime start = schedule.getDateTime();
        LocalDateTime end = schedule.getEndDateTime();
        if (start == null) {
            throw new IllegalArgumentException("開始時刻がない予定は送信できません（予定ID: " + schedule.getId() + "）。");
        }
        // 終了時刻機能を追加する前に作られた予定にも対応する。
        if (end == null || !end.isAfter(start)) {
            end = start.plusHours(1);
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("summary", schedule.getTitle());
        event.put("description", "AI予定秘書から同期（予定ID: " + schedule.getId() + "）");
        event.put("start", Map.of("dateTime", start.atZone(TOKYO).toOffsetDateTime().toString(), "timeZone", "Asia/Tokyo"));
        event.put("end", Map.of("dateTime", end.atZone(TOKYO).toOffsetDateTime().toString(), "timeZone", "Asia/Tokyo"));
        event.put("reminders", Map.of("useDefault", true));
        return event;
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new IllegalStateException("Google CalendarのClient IDとClient Secretが未設定です。");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record SyncResult(String eventId, String htmlLink) {}
}
