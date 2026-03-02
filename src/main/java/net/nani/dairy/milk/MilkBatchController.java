package net.nani.dairy.milk;


import net.nani.dairy.milk.dto.MilkBatchResponse;
import net.nani.dairy.milk.dto.MilkBatchQcEvaluationResponse;
import net.nani.dairy.milk.dto.MilkQcOverrideAuditResponse;
import net.nani.dairy.milk.dto.SaveMilkBatchRequest;
import net.nani.dairy.milk.dto.UpdateMilkBatchQcRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/qc-evaluation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','VET')")
    public MilkBatchQcEvaluationResponse qcEvaluation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Shift shift
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return service.evaluateQc(effectiveDate, shift);
    }

    @GetMapping("/qc-overrides")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<MilkQcOverrideAuditResponse> qcOverrides(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.listOverrideAudits(dateFrom, dateTo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER')")
    public MilkBatchResponse upsert(@Valid @RequestBody SaveMilkBatchRequest req) {
        return service.upsert(req);
    }

    @PostMapping("/qc")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MilkBatchResponse updateQc(@Valid @RequestBody UpdateMilkBatchQcRequest req, Authentication authentication) {
        return service.updateQc(
                req,
                actor(authentication),
                actorRole(authentication),
                hasRole(authentication, "ADMIN")
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            if (("ROLE_" + role).equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String actorRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "UNKNOWN";
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                return value.substring("ROLE_".length());
            }
        }
        return "UNKNOWN";
    }
}
