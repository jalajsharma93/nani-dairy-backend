package net.nani.dairy.health;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.health.dto.BreedingEventResponse;
import net.nani.dairy.health.dto.BreedingSummaryResponse;
import net.nani.dairy.health.dto.CreateBreedingEventRequest;
import net.nani.dairy.health.dto.CreateDewormingRequest;
import net.nani.dairy.health.dto.CreateVaccinationRequest;
import net.nani.dairy.health.dto.DewormingResponse;
import net.nani.dairy.health.dto.HealthProtocolResponse;
import net.nani.dairy.health.dto.HealthSummaryResponse;
import net.nani.dairy.health.dto.VaccinationResponse;
import net.nani.dairy.health.dto.WorklistResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/animals/{animalId}/vaccinations")
    public List<VaccinationResponse> listVaccinations(@PathVariable String animalId) {
        return healthService.listVaccinations(animalId);
    }

    @PostMapping("/animals/{animalId}/vaccinations")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public VaccinationResponse createVaccination(
            @PathVariable String animalId,
            @Valid @RequestBody CreateVaccinationRequest req
    ) {
        return healthService.createVaccination(animalId, req);
    }

    @PutMapping("/animals/{animalId}/vaccinations/{vaccinationId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public VaccinationResponse updateVaccination(
            @PathVariable String animalId,
            @PathVariable String vaccinationId,
            @Valid @RequestBody CreateVaccinationRequest req
    ) {
        return healthService.updateVaccination(animalId, vaccinationId, req);
    }

    @DeleteMapping("/animals/{animalId}/vaccinations/{vaccinationId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public void deleteVaccination(
            @PathVariable String animalId,
            @PathVariable String vaccinationId
    ) {
        healthService.deleteVaccination(animalId, vaccinationId);
    }

    @GetMapping("/animals/{animalId}/deworming")
    public List<DewormingResponse> listDeworming(@PathVariable String animalId) {
        return healthService.listDeworming(animalId);
    }

    @PostMapping("/animals/{animalId}/deworming")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public DewormingResponse createDeworming(
            @PathVariable String animalId,
            @Valid @RequestBody CreateDewormingRequest req
    ) {
        return healthService.createDeworming(animalId, req);
    }

    @PutMapping("/animals/{animalId}/deworming/{dewormingId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public DewormingResponse updateDeworming(
            @PathVariable String animalId,
            @PathVariable String dewormingId,
            @Valid @RequestBody CreateDewormingRequest req
    ) {
        return healthService.updateDeworming(animalId, dewormingId, req);
    }

    @DeleteMapping("/animals/{animalId}/deworming/{dewormingId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public void deleteDeworming(
            @PathVariable String animalId,
            @PathVariable String dewormingId
    ) {
        healthService.deleteDeworming(animalId, dewormingId);
    }

    @GetMapping("/health/summary")
    public HealthSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "7") Integer windowDays
    ) {
        return healthService.summary(date, windowDays);
    }

    @GetMapping("/animals/{animalId}/health-protocol")
    public HealthProtocolResponse healthProtocol(
            @PathVariable String animalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "7") Integer windowDays
    ) {
        return healthService.healthProtocol(animalId, date, windowDays);
    }

    @GetMapping("/worklist/today")
    public WorklistResponse todayWorklist(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "7") Integer windowDays
    ) {
        return healthService.todayWorklist(date, windowDays);
    }

    @GetMapping("/animals/{animalId}/breeding-events")
    public List<BreedingEventResponse> listBreedingEvents(@PathVariable String animalId) {
        return healthService.listBreedingEvents(animalId);
    }

    @PostMapping("/animals/{animalId}/breeding-events")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public BreedingEventResponse createBreedingEvent(
            @PathVariable String animalId,
            @Valid @RequestBody CreateBreedingEventRequest req
    ) {
        return healthService.createBreedingEvent(animalId, req);
    }

    @PutMapping("/animals/{animalId}/breeding-events/{breedingEventId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public BreedingEventResponse updateBreedingEvent(
            @PathVariable String animalId,
            @PathVariable String breedingEventId,
            @Valid @RequestBody CreateBreedingEventRequest req
    ) {
        return healthService.updateBreedingEvent(animalId, breedingEventId, req);
    }

    @DeleteMapping("/animals/{animalId}/breeding-events/{breedingEventId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public void deleteBreedingEvent(
            @PathVariable String animalId,
            @PathVariable String breedingEventId
    ) {
        healthService.deleteBreedingEvent(animalId, breedingEventId);
    }

    @GetMapping("/breeding/summary")
    public BreedingSummaryResponse breedingSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "7") Integer windowDays
    ) {
        return healthService.breedingSummary(date, windowDays);
    }
}
