package com.example.demo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class NotificationService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 4_000;

    private final ScheduleRepository scheduleRepository;
    private final AiNotificationRepository notificationRepository;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String apiKey;

    public NotificationService(ScheduleRepository scheduleRepository,
            AiNotificationRepository notificationRepository) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedRate = 60_000)
    public void scheduledCheck() {
        checkDueNotifications();
    }

    public int checkDueNotifications() {
        LocalDateTime now = LocalDateTime.now();
        int created = 0;

        for (Schedule schedule : scheduleRepository.findAll()) {
            LocalDateTime notificationTime = schedule.getDateTime()
                    .minusMinutes(schedule.getNotifyMinutesBefore());
            boolean isDue = !notificationTime.isBefore(now.minusSeconds(65))
                    && !notificationTime.isAfter(now.plusSeconds(5));

            if (isDue && !notificationRepository.existsByScheduleIdAndScheduledFor(
                    schedule.getId(), notificationTime)) {
                String message = createScheduleMessage(schedule);
                notificationRepository.save(new AiNotification(
                        schedule.getId(), schedule.getTitle(), notificationTime, message));
                created++;
            }
        }
        return created;
    }

    public String createGreeting() {
        return askGemini("短い朝の挨拶と、今日も開発を頑張ろうと思える一言を日本語で返してください。");
    }

    public String createGoalPlans(String title, String requirements) {
        String safeRequirements = requirements == null || requirements.isBlank()
                ? "特に条件はありません。無理なく続けられる形を提案してください。"
                : requirements;
        return askGemini("あなたはやさしい目標達成コーチです。ユーザーが次の目標を考えています。\n"
                + "目標: " + title + "\n"
                + "要望: \n" + safeRequirements + "\n\n"
                + "この目標に向けた計画候補を日本語で3つ出してください。"
                + "候補Aは無理なく継続、候補Bは短期集中、候補Cは基礎・準備重視にしてください。"
                + "それぞれに、最初の一週間にやる具体的な行動を箇条書きで2〜3個含めてください。"
                + "まだ予定を登録したり、断定したりせず、提案として優しく書いてください。");
    }

    public List<String> createTodayTodos(Goal goal) {
        String prompt = "あなたはやさしい目標達成コーチです。今日取り組むTodoを作ります。\n"
                + "目標: " + goal.getTitle() + "\n"
                + "ユーザーの要望: " + goal.getRequirements() + "\n"
                + "AIが作った計画: " + goal.getPlan() + "\n\n"
                + "今日できる、具体的で小さなTodoを3つだけ作ってください。"
                + "各Todoは1時間以内で終わる内容にし、JSON配列だけを返してください。"
                + "形式: [{\"title\":\"Todoの内容\"}]";
        try {
            JsonNode root = askGeminiJson(prompt);
            JsonNode items = root.isArray() ? root : root.path("todos");
            List<String> titles = new ArrayList<>();
            for (JsonNode item : items) {
                String title = item.path("title").asText().trim();
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
            if (!titles.isEmpty()) {
                return titles;
            }
        } catch (Exception ignored) {
            // JSON形式で返らない場合は、下の通常応答を1つのTodoとして保存します。
        }
        return List.of(askGemini("目標『" + goal.getTitle() + "』のために、今日最初にやるべき小さな行動を1つだけ日本語で提案してください。"));
    }

    private String createScheduleMessage(Schedule schedule) {
        String time = schedule.getDateTime().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        return askGemini("あなたは優しいAI予定秘書です。予定は『" + schedule.getTitle()
                + "』、開始は" + time + "です。開始まであと"
                + schedule.getNotifyMinutesBefore()
                + "分です。持ち物・移動・準備の観点を含めて、短く実用的な通知文を日本語で作ってください。"
        );
    }

    private String askGemini(String prompt) {
        String cleanApiKey = apiKey == null ? "" : apiKey.replaceAll("[\\s\\r\\n]+", "");
        if (cleanApiKey.isEmpty()) {
            return "Gemini APIキーが設定されていません。";
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
            String response = postGeminiWithRetry(cleanApiKey, body);
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();
            return text.isBlank() ? "AIから通知文を受け取れませんでした。" : text;
        } catch (Exception e) {
            return "AI通知を作成できませんでした: " + e.getMessage();
        }
    }

    private JsonNode askGeminiJson(String prompt) throws Exception {
        String cleanApiKey = apiKey == null ? "" : apiKey.replaceAll("[\\s\\r\\n]+", "");
        if (cleanApiKey.isEmpty()) {
            throw new IllegalStateException("Gemini APIキーが設定されていません。");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        body.putObject("generationConfig").put("responseMimeType", "application/json");
        String response = postGeminiWithRetry(cleanApiKey, body);
        String text = objectMapper.readTree(response).path("candidates").path(0).path("content")
                .path("parts").path(0).path("text").asText();
        return objectMapper.readTree(text);
    }

    private String postGeminiWithRetry(String cleanApiKey, ObjectNode body) throws Exception {
        Exception lastException = null;
        int maxAttempts = MAX_RETRIES + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri(GEMINI_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", cleanApiKey)
                        .body(body.toString())
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                lastException = e;
                if (!isRetryableStatus(e.getStatusCode().value()) || attempt == maxAttempts) {
                    throw e;
                }
                Thread.sleep(backoffDelayMs(attempt));
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt == maxAttempts) {
                    throw e;
                }
                Thread.sleep(backoffDelayMs(attempt));
            }
        }
        throw lastException;
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private long backoffDelayMs(int failedAttempt) {
        long delay = INITIAL_BACKOFF_MS * (1L << (failedAttempt - 1));
        return Math.min(delay, MAX_BACKOFF_MS);
    }
}
