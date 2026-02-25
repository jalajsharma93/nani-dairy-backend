package net.nani.dairy.feed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_recipe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedRecipeEntity {

    @Id
    @Column(name = "feed_recipe_id", length = 32, nullable = false, updatable = false)
    private String feedRecipeId;

    @Column(name = "recipe_name", length = 120, nullable = false)
    private String recipeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ration_phase", length = 32, nullable = false)
    private FeedRationPhase rationPhase;

    @Column(name = "target_animal_count")
    private Integer targetAnimalCount;

    @Column(name = "ingredients", length = 2000, nullable = false)
    private String ingredients;

    @Column(name = "instructions", length = 2000)
    private String instructions;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
