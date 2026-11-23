package com.carcare.journal;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.carcare.journal.JournalTypes.*;

@Entity @Table(name="service_records")
public class ServiceRecord extends JournalEntry {
    @Column(name="reminder_done",nullable=false) boolean reminderDone;
    @Column(nullable=false) LocalDate date;
    @Enumerated(EnumType.STRING) @Column(name="service_type", nullable=false, length=30) ServiceType serviceType;
    @Column(name="odometer_km") Integer odometerKm;
    @Column(nullable=false, length=2000) String description;
    @Column(length=120) String workshop;
    @Column(name="next_due_date") LocalDate nextDueDate;
    @Column(name="next_due_odometer_km") Integer nextDueOdometerKm;
    @Column(name="expense_id", unique=true) Long expenseId;
    @Column(name="odometer_entry_id", unique=true) Long odometerEntryId;
}
