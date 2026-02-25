package net.nani.dairy.sales;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.CreateCustomerRecordRequest;
import net.nani.dairy.sales.dto.CustomerRecordResponse;
import net.nani.dairy.sales.dto.RecordCustomerPayoutRequest;
import net.nani.dairy.sales.dto.UpdateCustomerRecordRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CustomerRecordController {

    private final CustomerRecordService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public List<CustomerRecordResponse> list(@RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerRecordResponse create(@Valid @RequestBody CreateCustomerRecordRequest req) {
        return service.create(req);
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerRecordResponse update(
            @PathVariable String customerId,
            @Valid @RequestBody UpdateCustomerRecordRequest req
    ) {
        return service.update(customerId, req);
    }

    @PostMapping("/{customerId}/payout")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerRecordResponse recordPayout(
            @PathVariable String customerId,
            @Valid @RequestBody RecordCustomerPayoutRequest req
    ) {
        return service.recordPayout(customerId, req);
    }
}
