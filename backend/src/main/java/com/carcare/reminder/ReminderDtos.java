package com.carcare.reminder;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class ReminderDtos {
    private ReminderDtos() {}
    public enum Status { OVERDUE, DUE, UPCOMING, SCHEDULED, DONE }
    public record Reminder(String key,String sourceKind,long sourceId,String type,String title,LocalDate dueDate,Integer dueOdometerKm,
        Long remainingDays,Long remainingKm,Status status,String notificationStage,String notificationKey) {}
    public record RemindersResponse(LocalDate asOfDate,String timeZone,int currentOdometerKm,List<Reminder> content) {}
    public record Completion(@NotNull Boolean done) {}
}
