package net.nani.dairy.milk;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.milk.dto.MilkEntryResponse;
import net.nani.dairy.milk.dto.SaveMilkEntriesRequest;
import net.nani.dairy.milk.dto.UpdateMilkEntryQcRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/milk-entries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MilkEntryController {

    private final MilkEntryService service;

    @GetMapping
    public List<MilkEntryResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Shift shift
    ) {
        return service.list(date, shift);
    }

    @GetMapping("/history")
    public List<MilkEntryResponse> historyByAnimal(
            @RequestParam String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.historyByAnimal(animalId, dateFrom, dateTo);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER')")
    public List<MilkEntryResponse> upsertEntries(@Valid @RequestBody SaveMilkEntriesRequest req) {
        return service.upsertEntries(req);
    }

    @PostMapping("/qc")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<MilkEntryResponse> updateQc(@Valid @RequestBody UpdateMilkEntryQcRequest req) {
        return service.updateQc(req);
    }
}
