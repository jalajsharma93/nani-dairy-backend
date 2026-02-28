package net.nani.dairy.sales;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.CreateCustomerSubscriptionLineRequest;
import net.nani.dairy.sales.dto.CustomerSubscriptionLineResponse;
import net.nani.dairy.sales.dto.UpdateCustomerSubscriptionLineRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/subscription-lines")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CustomerSubscriptionLineController {

    private final CustomerSubscriptionLineService subscriptionLineService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public List<CustomerSubscriptionLineResponse> list(
            @PathVariable String customerId,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return subscriptionLineService.list(customerId, activeOnly);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerSubscriptionLineResponse create(
            @PathVariable String customerId,
            @Valid @RequestBody CreateCustomerSubscriptionLineRequest req
    ) {
        return subscriptionLineService.create(customerId, req);
    }

    @PutMapping("/{subscriptionLineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerSubscriptionLineResponse update(
            @PathVariable String customerId,
            @PathVariable String subscriptionLineId,
            @Valid @RequestBody UpdateCustomerSubscriptionLineRequest req
    ) {
        return subscriptionLineService.update(customerId, subscriptionLineId, req);
    }

    @DeleteMapping("/{subscriptionLineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(
            @PathVariable String customerId,
            @PathVariable String subscriptionLineId
    ) {
        subscriptionLineService.delete(customerId, subscriptionLineId);
    }
}
