package net.nani.dairy.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedMaterialRepository extends JpaRepository<FeedMaterialEntity, String> {
    List<FeedMaterialEntity> findAllByOrderByMaterialNameAsc();
    Optional<FeedMaterialEntity> findByMaterialNameIgnoreCase(String materialName);
}
