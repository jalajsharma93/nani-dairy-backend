package net.nani.dairy.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeedLogRepository extends JpaRepository<FeedLogEntity, String> {
    List<FeedLogEntity> findByFeedDateOrderByCreatedAtDesc(LocalDate feedDate);
    List<FeedLogEntity> findByFeedDateBetweenOrderByFeedDateAscCreatedAtAsc(LocalDate fromDate, LocalDate toDate);

    List<FeedLogEntity> findByAnimalIdOrderByFeedDateDescCreatedAtDesc(String animalId);

    List<FeedLogEntity> findByFeedDateAndAnimalIdOrderByCreatedAtDesc(LocalDate feedDate, String animalId);

    List<FeedLogEntity> findAllByOrderByFeedDateDescCreatedAtDesc();
}
