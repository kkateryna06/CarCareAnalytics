package com.carcare.journal;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.carcare.journal.JournalTypes.*;

@Entity @Table(name="document_deadlines")
public class DocumentDeadline extends JournalEntry {
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) DocumentType type;
    @Column(name="valid_until", nullable=false) LocalDate validUntil;
    @Column(length=1000) String note;
}
