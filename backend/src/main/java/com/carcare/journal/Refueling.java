package com.carcare.journal;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.carcare.journal.JournalTypes.*;

@Entity @Table(name="refuelings")
public class Refueling extends JournalEntry {
    @Column(nullable=false) LocalDate date;
    @Column(name="odometer_km", nullable=false) Integer odometerKm;
    @Column(nullable=false, precision=10, scale=3) BigDecimal liters;
    @Column(name="is_full_tank", nullable=false) Boolean isFullTank;
    @Column(length=120) String station;
    @Column(length=1000) String note;
    @Column(name="expense_id", nullable=false, unique=true) Long expenseId;
    @Column(name="odometer_entry_id", nullable=false, unique=true) Long odometerEntryId;
}
