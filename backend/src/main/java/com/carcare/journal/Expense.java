package com.carcare.journal;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.carcare.journal.JournalTypes.*;

@Entity @Table(name="expenses")
public class Expense extends JournalEntry {
    @Column(nullable=false) LocalDate date;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) Category category;
    @Column(name="amount_pln", nullable=false, precision=12, scale=2) BigDecimal amountPln;
    @Column(nullable=false, length=200) String title;
    @Column(length=2000) String description;
    @Enumerated(EnumType.STRING) @Column(name="source_type", nullable=false, length=20) SourceType sourceType;
    @Column(name="source_id") Long sourceId;
}
