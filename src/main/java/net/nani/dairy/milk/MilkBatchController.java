package net.nani.dairy.milk;


import net.nani.dairy.milk.dto.MilkBatchResponse;
import net.nani.dairy.milk.dto.SaveMilkBatchRequest;
import net.nani.dairy.milk.dto.UpdateMilkBatchQcRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/milk-batches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // for mobile/web dev
public class MilkBatchController {

    private final MilkBatchService service;

    @GetMapping
    public MilkBatchResponse getByDateShift(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Shift shift
    ) {
        if (date == null) {
            date = LocalDate.now();
        }
        return service.get(date, shift);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER')")
    public MilkBatchResponse upsert(@Valid @RequestBody SaveMilkBatchRequest req) {
        return service.upsert(req);
    }

    @PostMapping("/qc")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MilkBatchResponse updateQc(@Valid @RequestBody UpdateMilkBatchQcRequest req) {
        return service.updateQc(req);
    }

}
