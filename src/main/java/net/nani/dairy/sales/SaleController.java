package net.nani.dairy.sales;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.CustomerLedgerRowResponse;
import net.nani.dairy.sales.dto.CreateSaleRequest;
import net.nani.dairy.sales.dto.CustomerSubscriptionInvoiceResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionInvoiceSummaryResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionStatementResponse;
import net.nani.dairy.sales.dto.DeliveryChecklistItemResponse;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkRequest;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkResponse;
import net.nani.dairy.sales.dto.MonthCloseSettlementRequest;
import net.nani.dairy.sales.dto.MonthCloseSettlementResponse;
import net.nani.dairy.sales.dto.ReconcileSaleRequest;
import net.nani.dairy.sales.dto.SaleComplianceOverrideAuditResponse;
import net.nani.dairy.sales.dto.SaleResponse;
import net.nani.dairy.sales.dto.SettlementReconciliationRowResponse;
import net.nani.dairy.sales.dto.SalesSummaryResponse;
import net.nani.dairy.sales.dto.UpdateSaleDeliveryRequest;
import net.nani.dairy.sales.dto.UpdateSaleRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<SaleResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) CustomerType customerType,
            @RequestParam(required = false) ProductType productType
    ) {
        return saleService.list(date, customerType, productType);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SaleResponse create(@Valid @RequestBody CreateSaleRequest req, Authentication authentication) {
        return saleService.create(req, actor(authentication));
    }

    @PutMapping("/{saleId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SaleResponse update(@PathVariable String saleId, @Valid @RequestBody UpdateSaleRequest req, Authentication authentication) {
        return saleService.update(saleId, req, actor(authentication));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SalesSummaryResponse dailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return saleService.dailySummary(date != null ? date : LocalDate.now());
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<CustomerLedgerRowResponse> ledger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to;
        return saleService.ledger(from, to);
    }

    @GetMapping("/subscription-statement")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerSubscriptionStatementResponse subscriptionStatement(
            @RequestParam String customerId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "false") boolean includeDaily
    ) {
        String effectiveMonth = month != null ? month : YearMonth.now().toString();
        return saleService.subscriptionStatement(customerId, effectiveMonth, includeDaily);
    }

    @GetMapping("/subscription-invoice")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CustomerSubscriptionInvoiceResponse subscriptionInvoice(
            @RequestParam String customerId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "false") boolean includeDaily
    ) {
        String effectiveMonth = month != null ? month : YearMonth.now().toString();
        return saleService.subscriptionInvoice(customerId, effectiveMonth, includeDaily);
    }

    @GetMapping("/subscription-invoices")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<CustomerSubscriptionInvoiceSummaryResponse> subscriptionInvoices(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) CustomerType customerType
    ) {
        String effectiveMonth = month != null ? month : YearMonth.now().toString();
        return saleService.subscriptionInvoices(effectiveMonth, customerType);
    }

    @GetMapping("/override-audits")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<SaleComplianceOverrideAuditResponse> overrideAudits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(30);
        return saleService.overrideAudits(from, to);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<SettlementReconciliationRowResponse> reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(30);
        return saleService.reconciliation(from, to);
    }

    @PostMapping("/{saleId}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SaleResponse reconcile(
            @PathVariable String saleId,
            @Valid @RequestBody ReconcileSaleRequest req,
            Authentication authentication
    ) {
        return saleService.reconcile(saleId, req, actor(authentication));
    }

    @PostMapping("/month-close")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MonthCloseSettlementResponse monthClose(
            @Valid @RequestBody MonthCloseSettlementRequest req,
            Authentication authentication
    ) {
        return saleService.monthCloseSettlement(req, actor(authentication));
    }

    @PostMapping("/month-close/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MonthCloseSettlementBulkResponse monthCloseBulk(
            @Valid @RequestBody MonthCloseSettlementBulkRequest req,
            Authentication authentication
    ) {
        return saleService.monthCloseSettlementBulk(req, actor(authentication));
    }

    @PostMapping("/month-close/bulk/preview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MonthCloseSettlementBulkResponse monthCloseBulkPreview(
            @Valid @RequestBody MonthCloseSettlementBulkRequest req,
            Authentication authentication
    ) {
        return saleService.monthCloseSettlementBulkPreview(req, actor(authentication));
    }

    @GetMapping("/delivery-list")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public List<DeliveryChecklistItemResponse> deliveryList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return saleService.deliveryList(date != null ? date : LocalDate.now());
    }

    @PostMapping("/{saleId}/delivery")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public DeliveryChecklistItemResponse updateDelivery(
            @PathVariable String saleId,
            @Valid @RequestBody UpdateSaleDeliveryRequest req,
            Authentication authentication
    ) {
        return saleService.updateDelivery(
                saleId,
                req,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (var authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            for (String role : roles) {
                if (("ROLE_" + role).equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
