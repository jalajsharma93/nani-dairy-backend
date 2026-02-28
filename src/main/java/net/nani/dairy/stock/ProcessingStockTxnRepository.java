package net.nani.dairy.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ProcessingStockTxnRepository extends JpaRepository<ProcessingStockTxnEntity, String> {
    List<ProcessingStockTxnEntity> findByTxnDateOrderByCreatedAtDesc(LocalDate txnDate);

    List<ProcessingStockTxnEntity> findAllByOrderByTxnDateDescCreatedAtDesc();

    List<ProcessingStockTxnEntity> findByTxnDateLessThanEqualOrderByTxnDateAscCreatedAtAsc(LocalDate txnDate);

    void deleteByTxnDateAndTxnTypeIn(LocalDate txnDate, Collection<ProcessingStockTxnType> txnTypes);
}
