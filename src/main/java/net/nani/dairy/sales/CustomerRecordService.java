package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.CreateCustomerRecordRequest;
import net.nani.dairy.sales.dto.CustomerRecordResponse;
import net.nani.dairy.sales.dto.RecordCustomerPayoutRequest;
import net.nani.dairy.sales.dto.UpdateCustomerRecordRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRecordService {

    private final CustomerRecordRepository repository;

    public List<CustomerRecordResponse> list(Boolean active) {
        List<CustomerRecordEntity> rows;
        if (active == null) {
            rows = repository.findAll();
        } else {
            rows = repository.findByIsActive(active);
        }
        return rows.stream()
                .sorted(
                        Comparator
                                .comparing(CustomerRecordEntity::isSubscriptionActive)
                                .reversed()
                                .thenComparing(row -> sortable(row.getRouteName()))
                                .thenComparing(row -> sortable(row.getCustomerName()))
                )
                .map(this::toResponse)
                .toList();
    }

    public CustomerRecordResponse create(CreateCustomerRecordRequest req) {
        validateSubscription(req.getSubscriptionActive(), req.getDailySubscriptionQty(), req.getSubscriptionFrequency());
        boolean subscriptionActive = Boolean.TRUE.equals(req.getSubscriptionActive());

        CustomerRecordEntity entity = CustomerRecordEntity.builder()
                .customerId(buildId())
                .customerName(normalizeRequired(req.getCustomerName(), "customerName is required"))
                .customerType(req.getCustomerType())
                .phone(trimToNull(req.getPhone()))
                .routeName(trimToNull(req.getRouteName()))
                .collectionPoint(trimToNull(req.getCollectionPoint()))
                .subscriptionActive(subscriptionActive)
                .dailySubscriptionQty(subscriptionActive ? req.getDailySubscriptionQty() : null)
                .subscriptionFrequency(subscriptionActive ? req.getSubscriptionFrequency() : null)
                .runningBalance(0.0)
                .totalPaid(0.0)
                .lastPayoutDate(null)
                .isActive(Boolean.TRUE.equals(req.getIsActive()))
                .notes(trimToNull(req.getNotes()))
                .build();

        return toResponse(repository.save(entity));
    }

    public CustomerRecordResponse update(String customerId, UpdateCustomerRecordRequest req) {
        validateSubscription(req.getSubscriptionActive(), req.getDailySubscriptionQty(), req.getSubscriptionFrequency());
        boolean subscriptionActive = Boolean.TRUE.equals(req.getSubscriptionActive());
        CustomerRecordEntity entity = repository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        entity.setCustomerName(normalizeRequired(req.getCustomerName(), "customerName is required"));
        entity.setCustomerType(req.getCustomerType());
        entity.setPhone(trimToNull(req.getPhone()));
        entity.setRouteName(trimToNull(req.getRouteName()));
        entity.setCollectionPoint(trimToNull(req.getCollectionPoint()));
        entity.setSubscriptionActive(subscriptionActive);
        entity.setDailySubscriptionQty(subscriptionActive ? req.getDailySubscriptionQty() : null);
        entity.setSubscriptionFrequency(subscriptionActive ? req.getSubscriptionFrequency() : null);
        entity.setActive(Boolean.TRUE.equals(req.getIsActive()));
        entity.setNotes(trimToNull(req.getNotes()));
        if (entity.getRunningBalance() == null) {
            entity.setRunningBalance(0.0);
        }
        if (entity.getTotalPaid() == null) {
            entity.setTotalPaid(0.0);
        }

        return toResponse(repository.save(entity));
    }

    @Transactional
    public CustomerRecordResponse recordPayout(String customerId, RecordCustomerPayoutRequest req) {
        CustomerRecordEntity entity = repository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        double amount = req.getAmount() == null ? 0 : req.getAmount();
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        double currentBalance = safeDouble(entity.getRunningBalance());
        if (amount > currentBalance) {
            throw new IllegalArgumentException("payout amount cannot exceed current running balance");
        }

        double nextBalance = currentBalance - amount;
        entity.setRunningBalance(nextBalance);
        entity.setTotalPaid(safeDouble(entity.getTotalPaid()) + amount);
        entity.setLastPayoutDate(req.getPayoutDate() != null ? req.getPayoutDate() : LocalDate.now());

        String payoutNote = trimToNull(req.getNote());
        if (payoutNote != null) {
            String prefix = "[PAYOUT " + entity.getLastPayoutDate() + " " + amount + "]";
            String existing = trimToNull(entity.getNotes());
            String combined = existing == null ? prefix + " " + payoutNote : existing + " | " + prefix + " " + payoutNote;
            entity.setNotes(combined.length() > 500 ? combined.substring(0, 500) : combined);
        }

        return toResponse(repository.save(entity));
    }

    private void validateSubscription(
            Boolean subscriptionActive,
            Double dailySubscriptionQty,
            SubscriptionFrequency subscriptionFrequency
    ) {
        boolean isSubscription = Boolean.TRUE.equals(subscriptionActive);
        if (!isSubscription) {
            return;
        }
        if (dailySubscriptionQty == null || dailySubscriptionQty <= 0) {
            throw new IllegalArgumentException("dailySubscriptionQty must be positive when subscriptionActive is true");
        }
        if (subscriptionFrequency == null) {
            throw new IllegalArgumentException("subscriptionFrequency is required when subscriptionActive is true");
        }
    }

    private String buildId() {
        return "CUS_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sortable(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private CustomerRecordResponse toResponse(CustomerRecordEntity entity) {
        return CustomerRecordResponse.builder()
                .customerId(entity.getCustomerId())
                .customerName(entity.getCustomerName())
                .customerType(entity.getCustomerType())
                .phone(entity.getPhone())
                .routeName(entity.getRouteName())
                .collectionPoint(entity.getCollectionPoint())
                .subscriptionActive(entity.isSubscriptionActive())
                .dailySubscriptionQty(entity.getDailySubscriptionQty())
                .subscriptionFrequency(entity.getSubscriptionFrequency())
                .runningBalance(safeDouble(entity.getRunningBalance()))
                .totalPaid(safeDouble(entity.getTotalPaid()))
                .lastPayoutDate(entity.getLastPayoutDate())
                .isActive(entity.isActive())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
