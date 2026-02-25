package net.nani.dairy.feed;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.feed.dto.CreateFeedLogRequest;
import net.nani.dairy.feed.dto.FeedLogResponse;
import net.nani.dairy.feed.dto.UpdateFeedLogRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedLogService {

    private final FeedLogRepository feedLogRepository;
    private final AnimalRepository animalRepository;

    public List<FeedLogResponse> list(LocalDate date, String animalId) {
        List<FeedLogEntity> rows;

        if (date != null && animalId != null && !animalId.trim().isEmpty()) {
            rows = feedLogRepository.findByFeedDateAndAnimalIdOrderByCreatedAtDesc(date, animalId.trim());
        } else if (date != null) {
            rows = feedLogRepository.findByFeedDateOrderByCreatedAtDesc(date);
        } else if (animalId != null && !animalId.trim().isEmpty()) {
            rows = feedLogRepository.findByAnimalIdOrderByFeedDateDescCreatedAtDesc(animalId.trim());
        } else {
            rows = feedLogRepository.findAllByOrderByFeedDateDescCreatedAtDesc();
        }

        return rows.stream().map(this::toResponse).toList();
    }

    public FeedLogResponse create(CreateFeedLogRequest req) {
        AnimalEntity animal = validateAnimal(req.getAnimalId());
        validateQuantity(req.getQuantityKg());

        FeedLogEntity entity = FeedLogEntity.builder()
                .feedLogId(buildId())
                .feedDate(req.getFeedDate())
                .animalId(req.getAnimalId().trim())
                .feedType(req.getFeedType().trim())
                .rationPhase(resolveRationPhase(req.getRationPhase(), animal.getStatus()))
                .quantityKg(req.getQuantityKg())
                .notes(trimToNull(req.getNotes()))
                .build();

        return toResponse(feedLogRepository.save(entity));
    }

    public FeedLogResponse update(String feedLogId, UpdateFeedLogRequest req) {
        AnimalEntity animal = validateAnimal(req.getAnimalId());
        validateQuantity(req.getQuantityKg());

        FeedLogEntity entity = feedLogRepository.findById(feedLogId)
                .orElseThrow(() -> new IllegalArgumentException("Feed log not found"));

        entity.setFeedDate(req.getFeedDate());
        entity.setAnimalId(req.getAnimalId().trim());
        entity.setFeedType(req.getFeedType().trim());
        entity.setRationPhase(resolveRationPhase(req.getRationPhase(), animal.getStatus()));
        entity.setQuantityKg(req.getQuantityKg());
        entity.setNotes(trimToNull(req.getNotes()));

        return toResponse(feedLogRepository.save(entity));
    }

    private AnimalEntity validateAnimal(String animalId) {
        String normalized = animalId == null ? "" : animalId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("animalId is required");
        }
        return animalRepository.findById(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found for animalId"));
    }

    private void validateQuantity(Double quantityKg) {
        if (quantityKg == null || Double.isNaN(quantityKg) || Double.isInfinite(quantityKg) || quantityKg <= 0) {
            throw new IllegalArgumentException("quantityKg must be a positive finite number");
        }
    }

    private String buildId() {
        return "FEED_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private FeedLogResponse toResponse(FeedLogEntity entity) {
        return FeedLogResponse.builder()
                .feedLogId(entity.getFeedLogId())
                .feedDate(entity.getFeedDate())
                .animalId(entity.getAnimalId())
                .feedType(entity.getFeedType())
                .rationPhase(entity.getRationPhase())
                .quantityKg(entity.getQuantityKg())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FeedRationPhase resolveRationPhase(FeedRationPhase requested, AnimalStatus animalStatus) {
        if (requested != null) {
            return requested;
        }
        if (animalStatus == AnimalStatus.LACTATING) {
            return FeedRationPhase.LACTATING;
        }
        if (animalStatus == AnimalStatus.SICK) {
            return FeedRationPhase.SICK_RECOVERY;
        }
        return FeedRationPhase.DRY;
    }
}
