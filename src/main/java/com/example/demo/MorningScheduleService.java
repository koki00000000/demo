package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MorningScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final LineSettingsRepository settingsRepository;
    private final LineNotificationService lineNotificationService;

    public MorningScheduleService(ScheduleRepository scheduleRepository,
            LineSettingsRepository settingsRepository, LineNotificationService lineNotificationService) {
        this.scheduleRepository = scheduleRepository;
        this.settingsRepository = settingsRepository;
        this.lineNotificationService = lineNotificationService;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Tokyo")
    public void sendMorningSchedule() {
        for (LineSettings settings : settingsRepository.findAllByOwnerUserIdIsNotNull()) {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
            List<Schedule> schedules = scheduleRepository.findAllByUserIdAndDateTimeBetweenOrderByDateTimeAsc(
                    settings.getOwnerUserId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            StringBuilder message = new StringBuilder("おはようございます！本日の予定は")
                    .append(schedules.size()).append("件です。");
            DateTimeFormatter time = DateTimeFormatter.ofPattern("H:mm");
            for (int i = 0; i < schedules.size(); i++) {
                Schedule schedule = schedules.get(i);
                LocalDateTime end = schedule.getEndDateTime() == null
                        ? schedule.getDateTime().plusHours(1) : schedule.getEndDateTime();
                message.append("\n【").append(i + 1).append(". ").append(schedule.getTitle()).append(" ")
                        .append(schedule.getDateTime().format(time)).append("〜").append(end.format(time)).append("】");
            }
            lineNotificationService.send(settings.getAccessToken(), settings.getUserId(), message.toString());
        }
    }
}
