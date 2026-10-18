package com.example.demo;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.multipart.MultipartFile;

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

    public DemoApplication(ScheduleRepository scheduleRepository,
            AiNotificationRepository notificationRepository,
            GoalRepository goalRepository,
            TodoRepository todoRepository,
            RoutineRepository routineRepository,
            NotificationService notificationService,
            LineNotificationService lineNotificationService) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.goalRepository = goalRepository;
        this.todoRepository = todoRepository;
        this.routineRepository = routineRepository;
        this.notificationService = notificationService;
        this.lineNotificationService = lineNotificationService;

        if (scheduleRepository.count() == 0) {
            scheduleRepository.save(new Schedule("Java Silverの勉強", LocalDateTime.now().plusHours(2), 30));
            scheduleRepository.save(new Schedule("自動運転スクールの教習", LocalDateTime.now().plusHours(4), 60));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping({"/api/schedules", "/api/events"})
    public List<Schedule> schedules() {
        return scheduleRepository.findAll();
    }

    @PostMapping({"/api/schedules", "/api/events"})
    public Schedule addSchedule(@RequestBody ScheduleRequest request) {
        return scheduleRepository.save(new Schedule(
                request.title(), request.dateTime(), request.endDateTime(),
                request.notifyMinutesBefore(), request.priority()));
    }

    @PatchMapping({"/api/schedules/{id}/time", "/api/events/{id}/time"})
    public Schedule moveSchedule(@org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody MoveScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("予定が見つかりません。"));
        schedule.setDateTime(request.dateTime());
        schedule.setEndDateTime(request.endDateTime());
        return scheduleRepository.save(schedule);
    }

    @DeleteMapping({"/api/schedules/{id}", "/api/events/{id}"})
    public Map<String, String> deleteSchedule(@org.springframework.web.bind.annotation.PathVariable Long id) {
        if (!scheduleRepository.existsById(id)) {
            return Map.of("message", "予定が見つかりませんでした。");
        }
        notificationRepository.deleteByScheduleId(id);
        scheduleRepository.deleteById(id);
        return Map.of("message", "予定を削除しました。");
    }

    @GetMapping("/api/notifications")
    public List<AiNotification> notifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
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

    @PostMapping("/api/assistant/image-schedules")
    public List<Schedule> addSchedulesFromImage(@RequestParam("image") MultipartFile image) throws Exception {
        if (image.isEmpty() || image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("画像ファイルを選択してください。");
        }
        if (image.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("画像は10MB以下にしてください。");
        }
        return notificationService.readSchedulesFromImage(image.getBytes(), image.getContentType()).stream()
                .map(item -> scheduleRepository.save(new Schedule(item.title(), item.dateTime(),
                        item.endDateTime(), 30, "medium")))
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
    public List<Goal> goals() {
        return goalRepository.findAllByActiveTrueOrderByCreatedAtDesc();
    }

    @PostMapping("/api/goals")
    public Goal startGoal(@RequestBody StartGoalRequest request) {
        return goalRepository.save(new Goal(request.title(), request.requirements(), request.plan()));
    }

    @DeleteMapping("/api/goals/{id}")
    public Map<String, String> deleteGoal(@org.springframework.web.bind.annotation.PathVariable Long id) {
        if (!goalRepository.existsById(id)) {
            return Map.of("message", "目標が見つかりませんでした。");
        }
        todoRepository.deleteByGoalId(id);
        goalRepository.deleteById(id);
        return Map.of("message", "目標と関連するTodoを削除しました。");
    }

    @GetMapping("/api/todos/today")
    public List<Todo> todayTodos() {
        return todoRepository.findAllByScheduledDateOrderByCreatedAtAsc(LocalDate.now());
    }

    @PostMapping("/api/goals/{id}/todos/generate")
    public List<Todo> generateTodayTodos(@org.springframework.web.bind.annotation.PathVariable Long id) {
        LocalDate today = LocalDate.now();
        List<Todo> existing = todoRepository.findAllByGoalIdAndScheduledDateOrderByCreatedAtAsc(id, today);
        if (!existing.isEmpty()) {
            return existing;
        }
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("目標が見つかりません。"));
        for (String title : notificationService.createTodayTodos(goal)) {
            todoRepository.save(new Todo(id, title, today));
        }
        return todoRepository.findAllByGoalIdAndScheduledDateOrderByCreatedAtAsc(id, today);
    }

    @PatchMapping("/api/todos/{id}/complete")
    public Todo toggleTodo(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Todoが見つかりません。"));
        todo.setCompleted(!todo.isCompleted());
        return todoRepository.save(todo);
    }

    @GetMapping("/api/routines")
    public List<Routine> routines() {
        return routineRepository.findAll();
    }

    @PostMapping("/api/routines")
    public Routine addRoutine(@RequestBody RoutineRequest request) {
        return routineRepository.save(new Routine(request.title()));
    }

    @PatchMapping("/api/routines/{id}/complete")
    public Routine toggleRoutine(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Routine routine = routineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルーティンが見つかりません。"));
        routine.setCompleted(!routine.isCompleted());
        return routineRepository.save(routine);
    }

    @DeleteMapping("/api/routines/{id}")
    public Map<String, String> deleteRoutine(@org.springframework.web.bind.annotation.PathVariable Long id) {
        routineRepository.deleteById(id);
        return Map.of("message", "ルーティンを削除しました。");
    }

    public record ScheduleRequest(String title, LocalDateTime dateTime, LocalDateTime endDateTime,
            int notifyMinutesBefore, String priority) {
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
}
