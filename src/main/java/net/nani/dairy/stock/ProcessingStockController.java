package net.nani.dairy.stock;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.stock.dto.AdjustProcessingStockRequest;
import net.nani.dairy.stock.dto.CreateProcessingConversionRequest;
import net.nani.dairy.stock.dto.ProcessingStockSummaryResponse;
import net.nani.dairy.stock.dto.ProcessingStockTxnResponse;
import net.nani.dairy.stock.dto.SyncProcessingDayRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-manager")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessingStockController {

    private final ProcessingStockService processingStockService;

    @GetMapping("/processing/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public ProcessingStockSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return processingStockService.summary(date);
    }

    @GetMapping("/processing/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public List<ProcessingStockTxnResponse> transactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return processingStockService.transactions(date);
    }

    @PostMapping("/processing/sync-day")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public ProcessingStockSummaryResponse syncDay(
            @Valid @RequestBody(required = false) SyncProcessingDayRequest req,
            Authentication authentication
    ) {
        return processingStockService.syncDay(req, actor(authentication));
    }

    @PostMapping("/processing/conversion")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public ProcessingStockTxnResponse conversion(
            @Valid @RequestBody CreateProcessingConversionRequest req,
            Authentication authentication
    ) {
        return processingStockService.addConversion(req, actor(authentication));
    }

    @PostMapping("/processing/adjustment")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public ProcessingStockTxnResponse adjustment(
            @Valid @RequestBody AdjustProcessingStockRequest req,
            Authentication authentication
    ) {
        return processingStockService.adjustStock(req, actor(authentication));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }
}
