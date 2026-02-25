package net.nani.dairy.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedRecipeRepository extends JpaRepository<FeedRecipeEntity, String> {
    List<FeedRecipeEntity> findAllByOrderByUpdatedAtDesc();
    List<FeedRecipeEntity> findByActiveTrueOrderByUpdatedAtDesc();
    List<FeedRecipeEntity> findByRationPhaseOrderByUpdatedAtDesc(FeedRationPhase rationPhase);
}
