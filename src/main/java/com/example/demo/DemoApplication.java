package com.example.demo;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

@SpringBootApplication
@EnableScheduling
@RestController
public class DemoApplication {

    private final ScheduleRepository scheduleRepository;
    private final AiNotificationRepository notificationRepository;
    private final GoalRepository goalRepository;
    private final TodoRepository todoRepository;
    private final RoutineRepository routineRepository;
    private final NotificationService notificationService;
    private final LineNotificationService lineNotificationService;
    private final LineSettingsRepository lineSettingsRepository;
    private final AppUserRepository appUserRepository;

    public DemoApplication(ScheduleRepository scheduleRepository,
            AiNotificationRepository notificationRepository,
            GoalRepository goalRepository,
            TodoRepository todoRepository,
            RoutineRepository routineRepository,
            NotificationService notificationService,
            LineNotificationService lineNotificationService,
            LineSettingsRepository lineSettingsRepository,
            AppUserRepository appUserRepository) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.goalRepository = goalRepository;
        this.todoRepository = todoRepository;
        this.routineRepository = routineRepository;
        this.notificationService = notificationService;
        this.lineNotificationService = lineNotificationService;
        this.lineSettingsRepository = lineSettingsRepository;
        this.appUserRepository = appUserRepository;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @PostMapping("/api/auth/register")
    public AuthResponse register(@RequestBody AuthRequest request, HttpSession session) {
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        if (appUserRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "そのユーザー名は使用されています。");
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        AppUser user = appUserRepository.save(new AppUser(username, hashPassword(request.password(), salt),
                Base64.getEncoder().encodeToString(salt)));
        session.setAttribute("userId", user.getId());
        return new AuthResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@RequestBody AuthRequest request, HttpSession session) {
        validatePassword(request.password());
        AppUser user = appUserRepository.findByUsername(normalizeUsername(request.username()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー名またはパスワードが違います。"));
        byte[] salt = Base64.getDecoder().decode(user.getPasswordSalt());
        byte[] actual = Base64.getDecoder().decode(hashPassword(request.password(), salt));
        byte[] expected = Base64.getDecoder().decode(user.getPasswordHash());
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー名またはパスワードが違います。");
        }
        session.setAttribute("userId", user.getId());
        return new AuthResponse(user.getId(), user.getUsername());
    }

    @GetMapping("/api/auth/me")
    public AuthResponse me(HttpSession session) {
        Long userId = currentUserId(session);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new AuthResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/api/auth/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("message", "ログアウトしました。");
    }

    @GetMapping({"/api/schedules", "/api/events"})
    public List<Schedule> schedules(HttpSession session) {
        return scheduleRepository.findAllByUserIdOrderByDateTimeAsc(currentUserId(session));
    }

    @PostMapping({"/api/schedules", "/api/events"})
    public Schedule addSchedule(@RequestBody ScheduleRequest request, HttpSession session) {
        Schedule schedule = new Schedule(
                request.title(), request.dateTime(), request.endDateTime(),
                request.notifyMinutesBefore(), request.priority(), request.category());
        schedule.setUserId(currentUserId(session));
        return scheduleRepository.save(schedule);
    }

    @PutMapping({"/api/schedules/{id}", "/api/events/{id}"})
    public Schedule updateSchedule(@org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody ScheduleRequest request, HttpSession session) {
        if (request.title() == null || request.title().isBlank() || request.dateTime() == null
                || request.endDateTime() == null || !request.endDateTime().isAfter(request.dateTime())) {
            throw new IllegalArgumentException("終了時刻は開始時刻より後にしてください。");
        }
        Schedule schedule = scheduleRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new IllegalArgumentException("予定が見つかりません。"));
        schedule.update(request.title(), request.dateTime(), request.endDateTime(),
                request.notifyMinutesBefore(), request.priority(), request.category());
        return scheduleRepository.save(schedule);
    }

    @PatchMapping({"/api/schedules/{id}/time", "/api/events/{id}/time"})
    public Schedule moveSchedule(@org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody MoveScheduleRequest request, HttpSession session) {
        Schedule schedule = scheduleRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new IllegalArgumentException("予定が見つかりません。"));
        schedule.setDateTime(request.dateTime());
        schedule.setEndDateTime(request.endDateTime());
        return scheduleRepository.save(schedule);
    }

    @DeleteMapping({"/api/schedules/{id}", "/api/events/{id}"})
    public Map<String, String> deleteSchedule(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Schedule schedule = scheduleRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "予定が見つかりません。"));
        notificationRepository.deleteByScheduleId(id);
        scheduleRepository.delete(schedule);
        return Map.of("message", "予定を削除しました。");
    }

    @GetMapping("/api/notifications")
    public List<AiNotification> notifications(HttpSession session) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId(session));
    }

    @PostMapping("/api/notifications/check")
    public Map<String, String> checkNotificationsNow() {
        int created = notificationService.checkDueNotifications();
        return Map.of("message", created + "件の通知を作成しました。");
    }

    @GetMapping("/ai/test")
    public String testGemini() {
        return notificationService.createGreeting();
    }

    @PostMapping("/api/assistant/advice")
    public Map<String, String> scheduleAdvice(@RequestBody ScheduleAdviceRequest request) {
        return Map.of("message", notificationService.createScheduleAdvice(request.title(), request.tone()));
    }

    @PostMapping("/api/assistant/chat")
    public Map<String, String> assistantChat(@RequestBody ChatRequest request) {
        return Map.of("message", notificationService.chat(request.message(), request.tone()));
    }

    @PostMapping("/api/line/test")
    public Map<String, String> testLine(@RequestBody LineMessageRequest request) {
        lineNotificationService.send(request.accessToken(), request.userId(),
                "【AI予定秘書】\nLINE通知のテストに成功しました！");
        return Map.of("message", "LINEへテスト通知を送りました。");
    }

    @PostMapping("/api/line/send")
    public Map<String, String> sendLine(@RequestBody LineMessageRequest request) {
        String scheduleTitle = request.scheduleTitle() == null || request.scheduleTitle().isBlank()
                ? "予定を確認してください。" : request.scheduleTitle();
        lineNotificationService.send(request.accessToken(), request.userId(),
                "【AI予定秘書】\n予定の時間になりました！\n" + scheduleTitle);
        return Map.of("message", "LINEへ通知を送りました。");
    }

    @PostMapping("/api/line/settings")
    public Map<String, String> saveLineSettings(@RequestBody LineMessageRequest request, HttpSession session) {
        Long ownerUserId = currentUserId(session);
        LineSettings settings = lineSettingsRepository.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> {
                    LineSettings created = new LineSettings(request.accessToken(), request.userId());
                    created.setOwnerUserId(ownerUserId);
                    return created;
                });
        settings.update(request.accessToken(), request.userId());
        lineSettingsRepository.save(settings);
        return Map.of("message", "LINE設定をサーバーへ保存しました。毎朝7:00に配信します。");
    }

    @PostMapping("/api/assistant/image-schedules")
    public List<Schedule> addSchedulesFromImage(@RequestParam("image") MultipartFile image, HttpSession session) throws Exception {
        if (image.isEmpty() || image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("画像ファイルを選択してください。");
        }
        if (image.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("画像は10MB以下にしてください。");
        }
        Long userId = currentUserId(session);
        return notificationService.readSchedulesFromImage(image.getBytes(), image.getContentType()).stream()
                .map(item -> {
                    Schedule schedule = new Schedule(item.title(), item.dateTime(), item.endDateTime(), 30, "medium");
                    schedule.setUserId(userId);
                    return scheduleRepository.save(schedule);
                })
                .toList();
    }

    @PostMapping("/api/assistant/reverse-schedule")
    public NotificationService.ReverseSchedule reverseSchedule(@RequestBody ReverseScheduleRequest request) {
        return notificationService.createReverseSchedule(request.todoTitle(), request.tone());
    }

    @PostMapping("/api/goals/plan")
    public Map<String, String> createGoalPlan(@RequestBody GoalPlanRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return Map.of("plan", "目標のタイトルを入力してください。");
        }
        return Map.of("plan", notificationService.createGoalPlans(request.title(), request.requirements()));
    }

    @GetMapping("/api/goals")
    public List<Goal> goals(HttpSession session) {
        return goalRepository.findAllByUserIdAndActiveTrueOrderByCreatedAtDesc(currentUserId(session));
    }

    @PostMapping("/api/goals")
    public Goal startGoal(@RequestBody StartGoalRequest request, HttpSession session) {
        Goal goal = new Goal(request.title(), request.requirements(), request.plan());
        goal.setUserId(currentUserId(session));
        return goalRepository.save(goal);
    }

    @DeleteMapping("/api/goals/{id}")
    public Map<String, String> deleteGoal(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Goal goal = goalRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目標が見つかりません。"));
        todoRepository.deleteByGoalId(id);
        goalRepository.delete(goal);
        return Map.of("message", "目標と関連するTodoを削除しました。");
    }

    @GetMapping("/api/todos/today")
    public List<Todo> todayTodos(HttpSession session) {
        return todoRepository.findAllByUserIdAndScheduledDateOrderByCreatedAtAsc(currentUserId(session), LocalDate.now());
    }

    @PostMapping("/api/goals/{id}/todos/generate")
    public List<Todo> generateTodayTodos(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        LocalDate today = LocalDate.now();
        List<Todo> existing = todoRepository.findAllByUserIdAndGoalIdAndScheduledDateOrderByCreatedAtAsc(userId, id, today);
        if (!existing.isEmpty()) {
            return existing;
        }
        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("目標が見つかりません。"));
        for (String title : notificationService.createTodayTodos(goal)) {
            Todo todo = new Todo(id, title, today);
            todo.setUserId(userId);
            todoRepository.save(todo);
        }
        return todoRepository.findAllByUserIdAndGoalIdAndScheduledDateOrderByCreatedAtAsc(userId, id, today);
    }

    @PatchMapping("/api/todos/{id}/complete")
    public Todo toggleTodo(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Todo todo = todoRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new IllegalArgumentException("Todoが見つかりません。"));
        todo.setCompleted(!todo.isCompleted());
        return todoRepository.save(todo);
    }

    @GetMapping("/api/routines")
    public List<Routine> routines(HttpSession session) {
        return routineRepository.findAllByUserIdOrderByIdAsc(currentUserId(session));
    }

    @PostMapping("/api/routines")
    public Routine addRoutine(@RequestBody RoutineRequest request, HttpSession session) {
        Routine routine = new Routine(request.title());
        routine.setUserId(currentUserId(session));
        return routineRepository.save(routine);
    }

    @PatchMapping("/api/routines/{id}/complete")
    public Routine toggleRoutine(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Routine routine = routineRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new IllegalArgumentException("ルーティンが見つかりません。"));
        routine.setCompleted(!routine.isCompleted());
        return routineRepository.save(routine);
    }

    @DeleteMapping("/api/routines/{id}")
    public Map<String, String> deleteRoutine(@org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        Routine routine = routineRepository.findByIdAndUserId(id, currentUserId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ルーティンが見つかりません。"));
        routineRepository.delete(routine);
        return Map.of("message", "ルーティンを削除しました。");
    }

    public record ScheduleRequest(String title, LocalDateTime dateTime, LocalDateTime endDateTime,
            int notifyMinutesBefore, String priority, String category) {
    }

    public record MoveScheduleRequest(LocalDateTime dateTime, LocalDateTime endDateTime) {}

    public record GoalPlanRequest(String title, String requirements) {
    }

    public record StartGoalRequest(String title, String requirements, String plan) {
    }

    public record ScheduleAdviceRequest(String title, String tone) {
    }

    public record ReverseScheduleRequest(String todoTitle, String tone) {}
    public record ChatRequest(String message, String tone) {}
    public record RoutineRequest(String title) {}
    public record LineMessageRequest(String accessToken, String userId, String scheduleTitle) {}
    public record AuthRequest(String username, String password) {}
    public record AuthResponse(Long id, String username) {}

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください。");
        return (Long) userId;
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!value.matches("[A-Za-z0-9_ぁ-んァ-ヶ一-龠]{3,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザー名は3〜30文字で入力してください。");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードは8文字以上で入力してください。");
        }
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("パスワードを処理できません。", e);
        }
    }
}
