package net.nani.dairy.health;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.health.dto.CreateMedicalTreatmentRequest;
import net.nani.dairy.health.dto.MedicalTreatmentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MedicalTreatmentController {

    private final MedicalTreatmentService medicalTreatmentService;

    @GetMapping("/animals/{animalId}/treatments")
    public List<MedicalTreatmentResponse> listTreatments(@PathVariable String animalId) {
        return medicalTreatmentService.listTreatments(animalId);
    }

    @PostMapping("/animals/{animalId}/treatments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public MedicalTreatmentResponse createTreatment(
            @PathVariable String animalId,
            @Valid @RequestBody CreateMedicalTreatmentRequest req
    ) {
        return medicalTreatmentService.createTreatment(animalId, req);
    }

    @PutMapping("/animals/{animalId}/treatments/{treatmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public MedicalTreatmentResponse updateTreatment(
            @PathVariable String animalId,
            @PathVariable String treatmentId,
            @Valid @RequestBody CreateMedicalTreatmentRequest req
    ) {
        return medicalTreatmentService.updateTreatment(animalId, treatmentId, req);
    }

    @DeleteMapping("/animals/{animalId}/treatments/{treatmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','VET')")
    public void deleteTreatment(
            @PathVariable String animalId,
            @PathVariable String treatmentId
    ) {
        medicalTreatmentService.deleteTreatment(animalId, treatmentId);
    }
}
