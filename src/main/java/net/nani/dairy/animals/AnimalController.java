package net.nani.dairy.animals;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.dto.AnimalResponse;
import net.nani.dairy.animals.dto.AnimalGenealogyResponse;
import net.nani.dairy.animals.dto.AnimalLifecycleEventResponse;
import net.nani.dairy.animals.dto.CreateAnimalRequest;
import net.nani.dairy.animals.dto.UpdateAnimalRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/animals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnimalController {

    private final AnimalService service;

    @GetMapping
    public List<AnimalResponse> listAnimals(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AnimalStatus status
    ) {
        return service.list(active, status);
    }

    @GetMapping("/by-tag")
    public AnimalResponse getAnimalByTag(@RequestParam String tag) {
        return service.getByTag(tag);
    }

    @GetMapping("/{animalId}")
    public AnimalResponse getAnimal(@PathVariable String animalId) {
        return service.getById(animalId);
    }

    @GetMapping("/{animalId}/genealogy")
    public AnimalGenealogyResponse getGenealogy(@PathVariable String animalId) {
        return service.genealogy(animalId);
    }

    @GetMapping("/{animalId}/lifecycle-history")
    public List<AnimalLifecycleEventResponse> getLifecycleHistory(@PathVariable String animalId) {
        return service.lifecycleHistory(animalId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AnimalResponse createAnimal(
            @Valid @RequestBody CreateAnimalRequest req,
            Authentication authentication
    ) {
        return service.create(req, actor(authentication));
    }

    @PutMapping("/{animalId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AnimalResponse updateAnimal(
            @PathVariable String animalId,
            @Valid @RequestBody UpdateAnimalRequest req,
            Authentication authentication
    ) {
        return service.update(animalId, req, actor(authentication));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }
}
