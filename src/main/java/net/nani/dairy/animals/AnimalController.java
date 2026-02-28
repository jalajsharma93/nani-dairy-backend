package net.nani.dairy.animals;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.dto.AnimalResponse;
import net.nani.dairy.animals.dto.CreateAnimalRequest;
import net.nani.dairy.animals.dto.UpdateAnimalRequest;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AnimalResponse createAnimal(@Valid @RequestBody CreateAnimalRequest req) {
        return service.create(req);
    }

    @PutMapping("/{animalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AnimalResponse updateAnimal(
            @PathVariable String animalId,
            @Valid @RequestBody UpdateAnimalRequest req
    ) {
        return service.update(animalId, req);
    }
}
