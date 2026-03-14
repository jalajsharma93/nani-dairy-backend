package net.nani.dairy.animals;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.dto.AnimalGenealogyResponse;
import net.nani.dairy.animals.dto.AnimalLifecycleEventResponse;
import net.nani.dairy.animals.dto.AnimalResponse;
import net.nani.dairy.animals.dto.CreateAnimalRequest;
import net.nani.dairy.animals.dto.UpdateAnimalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AnimalService {

    private static final DateTimeFormatter ANIMAL_ID_TS = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private final AnimalRepository repo;
    private final AnimalLifecycleEventRepository animalLifecycleEventRepository;

    public List<AnimalResponse> list(Boolean active, AnimalStatus status) {
        List<AnimalEntity> animals;

        if (active != null && status != null) {
            animals = repo.findByIsActiveAndStatus(active, status);
        } else if (active != null) {
            animals = repo.findByIsActive(active);
        } else if (status != null) {
            animals = repo.findByStatus(status);
        } else {
            animals = repo.findAll();
        }

        return animals.stream().map(this::toResponse).toList();
    }

    public AnimalResponse getById(String animalId) {
        var entity = repo.findById(animalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found"));
        return toResponse(entity);
    }

    public AnimalResponse getByTag(String tag) {
        String normalizedTag = normalize(tag);
        if (normalizedTag == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag is required");
        }
        var entity = repo.findByTagIgnoreCase(normalizedTag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found for tag"));
        return toResponse(entity);
    }

    public AnimalGenealogyResponse genealogy(String animalId) {
        var entity = repo.findById(animalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found"));
        var mother = resolveAnimalReference(entity.getMotherAnimalId()).orElse(null);
        var sire = resolveAnimalReference(entity.getSireTag()).orElse(null);
        var offspringById = new LinkedHashMap<String, AnimalEntity>();

        collectOffspring(offspringById, entity.getAnimalId(), true);
        collectOffspring(offspringById, entity.getTag(), true);
        collectOffspring(offspringById, entity.getAnimalId(), false);
        collectOffspring(offspringById, entity.getTag(), false);

        offspringById.remove(entity.getAnimalId());
        var offspring = offspringById.values().stream()
                .map(this::toResponse)
                .toList();

        return AnimalGenealogyResponse.builder()
                .animal(toResponse(entity))
                .mother(mother == null ? null : toResponse(mother))
                .sire(sire == null ? null : toResponse(sire))
                .offspring(offspring)
                .offspringCount(offspring.size())
                .activeOffspringCount((int) offspringById.values().stream().filter(AnimalEntity::isActive).count())
                .build();
    }

    public List<AnimalLifecycleEventResponse> lifecycleHistory(String animalId) {
        var normalizedAnimalId = normalize(animalId);
        if (normalizedAnimalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "animalId is required");
        }
        if (!repo.existsById(normalizedAnimalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found");
        }
        return animalLifecycleEventRepository.findByAnimalIdOrderByChangedAtDesc(normalizedAnimalId).stream()
                .map(this::toLifecycleEventResponse)
                .toList();
    }

    public AnimalResponse create(CreateAnimalRequest req, String actor) {
        validateDates(req.getDateOfBirth(), req.getWeaningDate(), req.getLastWeightDate());
        var normalizedTag = req.getTag().trim();
        var normalizedBreed = req.getBreed().trim();
        var generatedAnimalId = buildId(normalizedBreed);
        var motherRef = normalize(req.getMotherAnimalId());
        var sireRef = normalize(req.getSireTag());
        validateTagDifferent(normalizedTag, generatedAnimalId);
        validateUniqueTag(normalizedTag, null);
        validateLifecycleRules(null, req.getStatus(), req.getIsActive(), req.getGrowthStage());
        var parentage = validateAndResolveParentage(
                generatedAnimalId,
                normalizedTag,
                motherRef,
                sireRef,
                req.getDateOfBirth()
        );

        var entity = AnimalEntity.builder()
                .animalId(generatedAnimalId)
                .tag(normalizedTag)
                .name(normalize(req.getName()))
                .breed(normalizedBreed)
                .status(req.getStatus())
                .isActive(req.getIsActive())
                .motherAnimalId(parentage.motherAnimalId)
                .sireTag(parentage.sireTag)
                .dateOfBirth(req.getDateOfBirth())
                .growthStage(req.getGrowthStage())
                .birthWeightKg(req.getBirthWeightKg())
                .currentWeightKg(req.getCurrentWeightKg())
                .lastWeightDate(req.getLastWeightDate())
                .weaningDate(req.getWeaningDate())
                .weaningWeightKg(req.getWeaningWeightKg())
                .build();

        var saved = repo.save(entity);
        recordLifecycleEvent(
                saved.getAnimalId(),
                null,
                saved.getStatus(),
                null,
                saved.isActive(),
                firstNonBlank(normalize(req.getLifecycleReason()), "Animal created"),
                actor
        );
        return toResponse(saved);
    }

    public AnimalResponse update(String animalId, UpdateAnimalRequest req, String actor) {
        validateDates(req.getDateOfBirth(), req.getWeaningDate(), req.getLastWeightDate());
        var entity = repo.findById(animalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found"));

        var beforeStatus = entity.getStatus();
        var beforeActive = entity.isActive();

        var normalizedTag = req.getTag().trim();
        var motherRef = normalize(req.getMotherAnimalId());
        var sireRef = normalize(req.getSireTag());
        validateTagDifferent(normalizedTag, animalId);
        validateUniqueTag(normalizedTag, animalId);
        validateLifecycleRules(entity.getStatus(), req.getStatus(), req.getIsActive(), req.getGrowthStage());
        var parentage = validateAndResolveParentage(
                animalId,
                normalizedTag,
                motherRef,
                sireRef,
                req.getDateOfBirth()
        );

        entity.setTag(normalizedTag);
        entity.setName(normalize(req.getName()));
        entity.setBreed(req.getBreed().trim());
        entity.setStatus(req.getStatus());
        entity.setActive(req.getIsActive());
        entity.setMotherAnimalId(parentage.motherAnimalId);
        entity.setSireTag(parentage.sireTag);
        entity.setDateOfBirth(req.getDateOfBirth());
        entity.setGrowthStage(req.getGrowthStage());
        entity.setBirthWeightKg(req.getBirthWeightKg());
        entity.setCurrentWeightKg(req.getCurrentWeightKg());
        entity.setLastWeightDate(req.getLastWeightDate());
        entity.setWeaningDate(req.getWeaningDate());
        entity.setWeaningWeightKg(req.getWeaningWeightKg());

        var saved = repo.save(entity);
        if (beforeStatus != saved.getStatus() || beforeActive != saved.isActive()) {
            recordLifecycleEvent(
                    saved.getAnimalId(),
                    beforeStatus,
                    saved.getStatus(),
                    beforeActive,
                    saved.isActive(),
                    normalize(req.getLifecycleReason()),
                    actor
            );
        }
        return toResponse(saved);
    }

    private String buildId(String breed) {
        var breedCode = buildBreedCode(breed);
        for (int i = 0; i < 20; i++) {
            var dateTimePart = LocalDateTime.now().format(ANIMAL_ID_TS);
            var suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
            var candidate = breedCode + "_" + dateTimePart + "_" + suffix;
            if (!repo.existsById(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate animal ID");
    }

    private String buildBreedCode(String breed) {
        var sanitized = breed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (sanitized.isEmpty()) {
            return "ANM";
        }
        return sanitized.length() <= 3 ? sanitized : sanitized.substring(0, 3);
    }

    private AnimalResponse toResponse(AnimalEntity e) {
        return AnimalResponse.builder()
                .animalId(e.getAnimalId())
                .tag(e.getTag())
                .name(e.getName())
                .breed(e.getBreed())
                .status(e.getStatus())
                .isActive(e.isActive())
                .motherAnimalId(e.getMotherAnimalId())
                .sireTag(e.getSireTag())
                .dateOfBirth(e.getDateOfBirth())
                .growthStage(e.getGrowthStage())
                .birthWeightKg(e.getBirthWeightKg())
                .currentWeightKg(e.getCurrentWeightKg())
                .lastWeightDate(e.getLastWeightDate())
                .weaningDate(e.getWeaningDate())
                .weaningWeightKg(e.getWeaningWeightKg())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private AnimalLifecycleEventResponse toLifecycleEventResponse(AnimalLifecycleEventEntity event) {
        return AnimalLifecycleEventResponse.builder()
                .animalLifecycleEventId(event.getAnimalLifecycleEventId())
                .animalId(event.getAnimalId())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .fromActive(event.getFromActive())
                .toActive(event.isToActive())
                .reason(event.getReason())
                .changedBy(event.getChangedBy())
                .changedAt(event.getChangedAt())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateDates(LocalDate dateOfBirth, LocalDate weaningDate, LocalDate lastWeightDate) {
        if (dateOfBirth != null && weaningDate != null && weaningDate.isBefore(dateOfBirth)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weaning date cannot be before date of birth");
        }
        if (dateOfBirth != null && lastWeightDate != null && lastWeightDate.isBefore(dateOfBirth)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last weight date cannot be before date of birth");
        }
    }

    private void validateTagDifferent(String tag, String animalId) {
        if (tag.equalsIgnoreCase(animalId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag must be different from animal ID");
        }
    }

    private void recordLifecycleEvent(
            String animalId,
            AnimalStatus fromStatus,
            AnimalStatus toStatus,
            Boolean fromActive,
            boolean toActive,
            String reason,
            String actor
    ) {
        var event = AnimalLifecycleEventEntity.builder()
                .animalLifecycleEventId("ALE_" + UUID.randomUUID().toString().substring(0, 8))
                .animalId(animalId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .fromActive(fromActive)
                .toActive(toActive)
                .reason(firstNonBlank(reason, "Lifecycle updated"))
                .changedBy(normalizeActor(actor))
                .build();
        animalLifecycleEventRepository.save(event);
    }

    private String normalizeActor(String actor) {
        var normalized = normalize(actor);
        return normalized == null ? "unknown" : normalized;
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private void validateUniqueTag(String tag, String currentAnimalId) {
        repo.findByTagIgnoreCase(tag).ifPresent(existing -> {
            if (currentAnimalId == null || !existing.getAnimalId().equalsIgnoreCase(currentAnimalId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Animal tag already exists");
            }
        });
    }

    private void validateLifecycleRules(
            AnimalStatus existingStatus,
            AnimalStatus requestedStatus,
            Boolean isActive,
            AnimalGrowthStage growthStage
    ) {
        if (requestedStatus == null || isActive == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status and active flag are required");
        }
        if (requestedStatus == AnimalStatus.LACTATING && !isActive) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lactating animal must be active");
        }
        if ((requestedStatus == AnimalStatus.RETIRED
                || requestedStatus == AnimalStatus.SOLD
                || requestedStatus == AnimalStatus.DEAD) && isActive) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Retired/Sold/Dead animal must be inactive"
            );
        }
        if (growthStage == AnimalGrowthStage.CALF && requestedStatus == AnimalStatus.LACTATING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calf growth stage cannot be marked as lactating"
            );
        }
        if ((existingStatus == AnimalStatus.SOLD || existingStatus == AnimalStatus.DEAD)
                && existingStatus != requestedStatus) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sold/Dead lifecycle status is terminal and cannot be changed"
            );
        }
    }

    private ResolvedParentage validateAndResolveParentage(
            String childAnimalId,
            String childTag,
            String motherReference,
            String sireReference,
            LocalDate childDateOfBirth
    ) {
        AnimalEntity mother = null;
        AnimalEntity sire = null;
        String motherAnimalId = null;
        String sireTag = null;

        if (motherReference != null) {
            mother = resolveAnimalReference(motherReference)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mother animal not found"));
            validateParentNotSelf(childAnimalId, childTag, mother.getAnimalId(), mother.getTag(), "Mother");
            validateNoAncestryCycle(childAnimalId, mother, "Mother");
            if (childDateOfBirth != null && mother.getDateOfBirth() != null && childDateOfBirth.isBefore(mother.getDateOfBirth())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Animal date of birth cannot be before mother date of birth"
                );
            }
            motherAnimalId = mother.getAnimalId();
        }

        if (sireReference != null) {
            sire = resolveAnimalReference(sireReference).orElse(null);
            if (sire != null) {
                validateParentNotSelf(childAnimalId, childTag, sire.getAnimalId(), sire.getTag(), "Sire");
                validateNoAncestryCycle(childAnimalId, sire, "Sire");
                if (childDateOfBirth != null && sire.getDateOfBirth() != null && childDateOfBirth.isBefore(sire.getDateOfBirth())) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                            "Animal date of birth cannot be before sire date of birth"
                    );
                }
                sireTag = sire.getTag();
            } else {
                if (sireReference.equalsIgnoreCase(childAnimalId) || sireReference.equalsIgnoreCase(childTag)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sire reference cannot be self");
                }
                sireTag = sireReference;
            }
        }

        if (mother != null && sire != null && mother.getAnimalId().equalsIgnoreCase(sire.getAnimalId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mother and sire cannot reference same animal");
        }

        return new ResolvedParentage(motherAnimalId, sireTag);
    }

    private void validateParentNotSelf(
            String childAnimalId,
            String childTag,
            String parentAnimalId,
            String parentTag,
            String relation
    ) {
        if (parentAnimalId != null && parentAnimalId.equalsIgnoreCase(childAnimalId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, relation + " cannot be the same animal");
        }
        if (parentTag != null && parentTag.equalsIgnoreCase(childTag)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, relation + " cannot use same tag as child");
        }
    }

    private void validateNoAncestryCycle(String childAnimalId, AnimalEntity parent, String relation) {
        if (childAnimalId == null || parent == null) {
            return;
        }
        var normalizedChildId = normalize(childAnimalId);
        if (normalizedChildId == null) {
            return;
        }

        var stack = new ArrayDeque<AnimalEntity>();
        Set<String> visited = new HashSet<>();
        stack.push(parent);

        while (!stack.isEmpty()) {
            var current = stack.pop();
            var currentId = normalize(current.getAnimalId());
            if (currentId == null) {
                continue;
            }

            if (currentId.equalsIgnoreCase(normalizedChildId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        relation + " link creates genealogy cycle"
                );
            }

            var visitKey = currentId.toLowerCase(Locale.ROOT);
            if (!visited.add(visitKey)) {
                continue;
            }

            resolveAnimalReference(current.getMotherAnimalId()).ifPresent(stack::push);
            resolveAnimalReference(current.getSireTag()).ifPresent(stack::push);
        }
    }

    private Optional<AnimalEntity> resolveAnimalReference(String reference) {
        var normalizedReference = normalize(reference);
        if (normalizedReference == null) {
            return Optional.empty();
        }
        return repo.findByAnimalIdIgnoreCase(normalizedReference)
                .or(() -> repo.findByTagIgnoreCase(normalizedReference));
    }

    private void collectOffspring(
            LinkedHashMap<String, AnimalEntity> offspringById,
            String reference,
            boolean isMotherRef
    ) {
        var normalizedReference = normalize(reference);
        if (normalizedReference == null) {
            return;
        }
        var rows = isMotherRef
                ? repo.findByMotherAnimalIdIgnoreCase(normalizedReference)
                : repo.findBySireTagIgnoreCase(normalizedReference);
        for (AnimalEntity row : rows) {
            offspringById.putIfAbsent(row.getAnimalId(), row);
        }
    }

    private static class ResolvedParentage {
        private final String motherAnimalId;
        private final String sireTag;

        private ResolvedParentage(String motherAnimalId, String sireTag) {
            this.motherAnimalId = motherAnimalId;
            this.sireTag = sireTag;
        }
    }
}
