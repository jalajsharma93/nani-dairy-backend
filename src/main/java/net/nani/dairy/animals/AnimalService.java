package net.nani.dairy.animals;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.dto.AnimalResponse;
import net.nani.dairy.animals.dto.CreateAnimalRequest;
import net.nani.dairy.animals.dto.UpdateAnimalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AnimalService {

    private static final DateTimeFormatter ANIMAL_ID_TS = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private final AnimalRepository repo;

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

    public AnimalResponse create(CreateAnimalRequest req) {
        validateDates(req.getDateOfBirth(), req.getWeaningDate(), req.getLastWeightDate());
        var normalizedTag = req.getTag().trim();
        var normalizedBreed = req.getBreed().trim();
        var generatedAnimalId = buildId(normalizedBreed);
        validateTagDifferent(normalizedTag, generatedAnimalId);

        var entity = AnimalEntity.builder()
                .animalId(generatedAnimalId)
                .tag(normalizedTag)
                .name(normalize(req.getName()))
                .breed(normalizedBreed)
                .status(req.getStatus())
                .isActive(req.getIsActive())
                .motherAnimalId(normalize(req.getMotherAnimalId()))
                .sireTag(normalize(req.getSireTag()))
                .dateOfBirth(req.getDateOfBirth())
                .growthStage(req.getGrowthStage())
                .birthWeightKg(req.getBirthWeightKg())
                .currentWeightKg(req.getCurrentWeightKg())
                .lastWeightDate(req.getLastWeightDate())
                .weaningDate(req.getWeaningDate())
                .weaningWeightKg(req.getWeaningWeightKg())
                .build();

        return toResponse(repo.save(entity));
    }

    public AnimalResponse update(String animalId, UpdateAnimalRequest req) {
        validateDates(req.getDateOfBirth(), req.getWeaningDate(), req.getLastWeightDate());
        var normalizedTag = req.getTag().trim();
        validateTagDifferent(normalizedTag, animalId);

        var entity = repo.findById(animalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal not found"));

        entity.setTag(normalizedTag);
        entity.setName(normalize(req.getName()));
        entity.setBreed(req.getBreed().trim());
        entity.setStatus(req.getStatus());
        entity.setActive(req.getIsActive());
        entity.setMotherAnimalId(normalize(req.getMotherAnimalId()));
        entity.setSireTag(normalize(req.getSireTag()));
        entity.setDateOfBirth(req.getDateOfBirth());
        entity.setGrowthStage(req.getGrowthStage());
        entity.setBirthWeightKg(req.getBirthWeightKg());
        entity.setCurrentWeightKg(req.getCurrentWeightKg());
        entity.setLastWeightDate(req.getLastWeightDate());
        entity.setWeaningDate(req.getWeaningDate());
        entity.setWeaningWeightKg(req.getWeaningWeightKg());

        return toResponse(repo.save(entity));
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
}
