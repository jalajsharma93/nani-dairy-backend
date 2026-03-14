package net.nani.dairy.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedProcurementRunRepository extends JpaRepository<FeedProcurementRunEntity, String> {
    List<FeedProcurementRunEntity> findTop50ByOrderByCreatedAtDesc();
}
