package com.carcare.journal;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.carcare.journal.JournalTypes.*;

@Entity @Table(name="odometer_entries")
public class OdometerEntry extends JournalEntry {
    @Column(nullable=false) LocalDate date;
    @Column(name="odometer_km", nullable=false) Integer odometerKm;
    @Column(length=1000) String note;
    @Enumerated(EnumType.STRING) @Column(name="source_type", nullable=false, length=20) SourceType sourceType;
}
