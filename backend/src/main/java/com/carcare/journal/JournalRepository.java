package com.carcare.journal;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@NoRepositoryBean
public interface JournalRepository<T extends JournalEntry> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    @Query("select e.vehicleId from #{#entityName} e where e.id = :id")
    Optional<Long> vehicleIdFor(@Param("id") long id);
}
