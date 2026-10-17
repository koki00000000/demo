package com.example.demo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
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

    private static final String[] GEMINI_MODELS = {
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash"
    };
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2_000;
    private static final String BUSY_MESSAGE =
            "ただいまAIが混雑しています。数秒後にもう一度ボタンを押してください";
    private static final String RESPONSE_CONSTRAINT =
            "\n\n【制約条件】\n"
                    + "・回答は要点のみを絞り、全体で 200 文字〜300 文字程度の簡潔な文章にしてください。\n"
                    + "・箇条書きを活用し、長々とした前置きや挨拶は一切省略してください。";

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

    public String createScheduleAdvice(String title, String tone) {
        return askGemini("あなたは" + toneInstruction(tone) + "AI予定秘書です。予定『" + title
                + "』に対して、準備の助言と前向きな励ましを日本語で返してください。"
                + "勉強なら集中のコツ、外出やカフェなら持ち物・予算・移動の観点を優先してください。"
                + "駅から駅への移動が含まれる場合は、推定所要時間は断定せず目安として示し、経路検索での確認を促してください。");
    }

    public String chat(String message, String tone) {
        return askGemini("あなたは" + toneInstruction(tone) + "AI予定秘書です。"
                + "予定・目標・Todoについて、実行しやすい具体策を日本語で答えてください。相談: " + message);
    }

    public List<ParsedSchedule> readSchedulesFromImage(byte[] image, String mimeType) throws Exception {
        String prompt = "画像から予定のタイトルと開始日時を読み取ってください。現在日時は"
                + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "です。"
                + "年がない場合は次に訪れる妥当な日付を推定してください。読み取れない項目を捏造しないでください。"
                + "終了日時が画像にない場合は開始の1時間後にしてください。JSON配列だけを返してください。"
                + "形式: [{\"title\":\"予定名\",\"dateTime\":\"yyyy-MM-dd'T'HH:mm\",\"endDateTime\":\"yyyy-MM-dd'T'HH:mm\"}]";
        JsonNode root = askGeminiImageJson(prompt, image, mimeType);
        List<ParsedSchedule> schedules = new ArrayList<>();
        for (JsonNode item : root.isArray() ? root : root.path("schedules")) {
            String title = item.path("title").asText().trim();
            String dateTime = item.path("dateTime").asText().trim();
            String endDateTime = item.path("endDateTime").asText().trim();
            if (!title.isEmpty() && !dateTime.isEmpty()) {
                LocalDateTime start = LocalDateTime.parse(dateTime);
                schedules.add(new ParsedSchedule(title, start,
                        endDateTime.isEmpty() ? start.plusHours(1) : LocalDateTime.parse(endDateTime)));
            }
        }
        return schedules;
    }

    public ReverseSchedule createReverseSchedule(String todoTitle, String tone) {
        try {
            String prompt = "あなたは" + toneInstruction(tone) + "AI予定秘書です。現在日時は"
                    + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "です。"
                    + "Todo『" + todoTitle + "』を今日または次の実行可能な時間帯へ配置してください。"
                    + "所要時間は30〜90分で、過去時刻にはしないでください。JSONだけを返してください。"
                    + "形式: {\"title\":\"予定名\",\"dateTime\":\"yyyy-MM-dd'T'HH:mm\",\"message\":\"提案文\"}";
            JsonNode result = askGeminiJson(prompt);
            return new ReverseSchedule(result.path("title").asText(todoTitle),
                    LocalDateTime.parse(result.path("dateTime").asText()), result.path("message").asText());
        } catch (Exception e) {
            LocalDateTime fallback = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
            return new ReverseSchedule(todoTitle, fallback,
                    "では" + fallback.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) + "から取り組みましょう。");
        }
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
        } catch (GeminiUnavailableException e) {
            return List.of(BUSY_MESSAGE);
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
            body.putArray("contents").addObject().putArray("parts").addObject()
                    .put("text", prompt + RESPONSE_CONSTRAINT);
            String response = postGeminiWithRetry(cleanApiKey, body);
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();
            return text.isBlank() ? "AIから通知文を受け取れませんでした。" : text;
        } catch (Exception e) {
            return BUSY_MESSAGE;
        }
    }

    private JsonNode askGeminiJson(String prompt) throws Exception {
        String cleanApiKey = apiKey == null ? "" : apiKey.replaceAll("[\\s\\r\\n]+", "");
        if (cleanApiKey.isEmpty()) {
            throw new IllegalStateException("Gemini APIキーが設定されていません。");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject()
                .put("text", prompt + RESPONSE_CONSTRAINT);
        body.putObject("generationConfig").put("responseMimeType", "application/json");
        String response = postGeminiWithRetry(cleanApiKey, body);
        String text = objectMapper.readTree(response).path("candidates").path(0).path("content")
                .path("parts").path(0).path("text").asText();
        return objectMapper.readTree(text);
    }

    private JsonNode askGeminiImageJson(String prompt, byte[] image, String mimeType) throws Exception {
        String cleanApiKey = apiKey == null ? "" : apiKey.replaceAll("[\\s\\r\\n]+", "");
        if (cleanApiKey.isEmpty()) throw new IllegalStateException("Gemini APIキーが設定されていません。");
        ObjectNode body = objectMapper.createObjectNode();
        var parts = body.putArray("contents").addObject().putArray("parts");
        parts.addObject().put("text", prompt);
        parts.addObject().putObject("inline_data").put("mime_type", mimeType)
                .put("data", Base64.getEncoder().encodeToString(image));
        body.putObject("generationConfig").put("responseMimeType", "application/json");
        String response = postGeminiWithRetry(cleanApiKey, body);
        String text = objectMapper.readTree(response).path("candidates").path(0).path("content")
                .path("parts").path(0).path("text").asText();
        return objectMapper.readTree(text);
    }

    private String toneInstruction(String tone) {
        return switch (tone == null ? "friendly" : tone) {
            case "strict" -> "簡潔で厳格なしっかり者の";
            case "gentle" -> "安心感のある優しい";
            default -> "明るく親しみやすい";
        };
    }

    public record ParsedSchedule(String title, LocalDateTime dateTime, LocalDateTime endDateTime) {}
    public record ReverseSchedule(String title, LocalDateTime dateTime, String message) {}

    private String postGeminiWithRetry(String cleanApiKey, ObjectNode body) throws Exception {
        for (String model : GEMINI_MODELS) {
            String url = geminiGenerateUrl(model);
            int maxAttempts = MAX_RETRIES + 1;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return restClient.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-goog-api-key", cleanApiKey)
                            .body(body.toString())
                            .retrieve()
                            .body(String.class);
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (status == 404) {
                        break;
                    }
                    if (isCongestedStatus(status) && attempt <= MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    break;
                } catch (ResourceAccessException e) {
                    if (attempt <= MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    break;
                }
            }
        }
        throw new GeminiUnavailableException();
    }

    private String geminiGenerateUrl(String model) {
        String modelId = model.startsWith("models/") ? model.substring("models/".length()) : model;
        return "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent";
    }

    private boolean isCongestedStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static final class GeminiUnavailableException extends RuntimeException {
        GeminiUnavailableException() {
            super(BUSY_MESSAGE);
        }
    }
}
