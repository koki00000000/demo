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
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
@RestController
public class DemoApplication {

    private final ScheduleRepository scheduleRepository;
    private final AiNotificationRepository notificationRepository;
    private final GoalRepository goalRepository;
    private final TodoRepository todoRepository;
    private final NotificationService notificationService;

    public DemoApplication(ScheduleRepository scheduleRepository,
            AiNotificationRepository notificationRepository,
            GoalRepository goalRepository,
            TodoRepository todoRepository,
            NotificationService notificationService) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.goalRepository = goalRepository;
        this.todoRepository = todoRepository;
        this.notificationService = notificationService;

        if (scheduleRepository.count() == 0) {
            scheduleRepository.save(new Schedule("Java Silverの勉強", LocalDateTime.now().plusHours(2), 30));
            scheduleRepository.save(new Schedule("自動運転スクールの教習", LocalDateTime.now().plusHours(4), 60));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/api/schedules")
    public List<Schedule> schedules() {
        return scheduleRepository.findAll();
    }

    @PostMapping("/api/schedules")
    public Schedule addSchedule(@RequestBody ScheduleRequest request) {
        return scheduleRepository.save(new Schedule(
                request.title(), request.dateTime(), request.notifyMinutesBefore()));
    }

    @DeleteMapping("/api/schedules/{id}")
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
        return Map.of("message", notificationService.createScheduleAdvice(request.title()));
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

    public record ScheduleRequest(String title, LocalDateTime dateTime, int notifyMinutesBefore) {
    }

    public record GoalPlanRequest(String title, String requirements) {
    }

    public record StartGoalRequest(String title, String requirements, String plan) {
    }

    public record ScheduleAdviceRequest(String title) {
    }
}
