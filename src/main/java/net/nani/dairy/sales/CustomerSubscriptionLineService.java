package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.CreateCustomerSubscriptionLineRequest;
import net.nani.dairy.sales.dto.CustomerSubscriptionLineResponse;
import net.nani.dairy.sales.dto.UpdateCustomerSubscriptionLineRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerSubscriptionLineService {

    private final CustomerSubscriptionLineRepository subscriptionLineRepository;
    private final CustomerRecordRepository customerRecordRepository;

    public List<CustomerSubscriptionLineResponse> list(String customerId, Boolean activeOnly) {
        validateCustomer(customerId);
        List<CustomerSubscriptionLineEntity> rows = Boolean.TRUE.equals(activeOnly)
                ? subscriptionLineRepository.findByCustomerIdAndActiveTrueOrderByTaskShiftAscPreferredTimeAscCreatedAtAsc(customerId)
                : subscriptionLineRepository.findByCustomerIdOrderByTaskShiftAscPreferredTimeAscCreatedAtAsc(customerId);
        return rows.stream()
                .sorted(Comparator
                        .comparing((CustomerSubscriptionLineEntity row) -> row.getTaskShift().name())
                        .thenComparing(row -> row.getPreferredTime() != null ? row.getPreferredTime().toString() : "")
                        .thenComparing(row -> row.getProductType().name()))
                .map(this::toResponse)
                .toList();
    }

    public CustomerSubscriptionLineResponse create(String customerId, CreateCustomerSubscriptionLineRequest req) {
        CustomerRecordEntity customer = validateCustomer(customerId);
        // Make line creation resilient: if planner/customer lifecycle was not toggled yet,
        // auto-enable subscription defaults from the line being created.
        if (!customer.isSubscriptionActive()) {
            customer.setSubscriptionActive(true);
        }
        if (customer.getSubscriptionFrequency() == null) {
            customer.setSubscriptionFrequency(SubscriptionFrequency.DAILY);
        }
        if (customer.getDailySubscriptionQty() == null || customer.getDailySubscriptionQty() <= 0) {
            customer.setDailySubscriptionQty(req.getQuantity());
        }
        if (customer.getDefaultMilkUnitPrice() == null || customer.getDefaultMilkUnitPrice() <= 0) {
            customer.setDefaultMilkUnitPrice(req.getUnitPrice());
        }
        customerRecordRepository.save(customer);
        validateDateWindow(req.getStartDate(), req.getEndDate());

        CustomerSubscriptionLineEntity entity = CustomerSubscriptionLineEntity.builder()
                .subscriptionLineId(buildId())
                .customerId(customerId)
                .taskShift(req.getTaskShift())
                .productType(req.getProductType())
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .preferredTime(req.getPreferredTime())
                .activeDaysCsv(normalizeDaysCsv(req.getActiveDaysCsv()))
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .active(req.getActive() == null || req.getActive())
                .notes(trimToNull(req.getNotes()))
                .build();

        return toResponse(subscriptionLineRepository.save(entity));
    }

    public CustomerSubscriptionLineResponse update(
            String customerId,
            String subscriptionLineId,
            UpdateCustomerSubscriptionLineRequest req
    ) {
        validateCustomer(customerId);
        validateDateWindow(req.getStartDate(), req.getEndDate());
        CustomerSubscriptionLineEntity entity = subscriptionLineRepository
                .findBySubscriptionLineIdAndCustomerId(subscriptionLineId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription line not found"));

        entity.setTaskShift(req.getTaskShift());
        entity.setProductType(req.getProductType());
        entity.setQuantity(req.getQuantity());
        entity.setUnitPrice(req.getUnitPrice());
        entity.setPreferredTime(req.getPreferredTime());
        entity.setActiveDaysCsv(normalizeDaysCsv(req.getActiveDaysCsv()));
        entity.setStartDate(req.getStartDate());
        entity.setEndDate(req.getEndDate());
        entity.setActive(Boolean.TRUE.equals(req.getActive()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toResponse(subscriptionLineRepository.save(entity));
    }

    public void delete(String customerId, String subscriptionLineId) {
        validateCustomer(customerId);
        CustomerSubscriptionLineEntity entity = subscriptionLineRepository
                .findBySubscriptionLineIdAndCustomerId(subscriptionLineId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription line not found"));
        subscriptionLineRepository.delete(entity);
    }

    private CustomerRecordEntity validateCustomer(String customerId) {
        String normalizedCustomerId = trimToNull(customerId);
        if (normalizedCustomerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        return customerRecordRepository.findById(normalizedCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
    }

    private String normalizeDaysCsv(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return allDaysCsv();
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        String[] tokens = raw.split("[,\\s]+");
        for (String token : tokens) {
            DayOfWeek day = parseDay(token);
            if (day != null) {
                days.add(day);
            }
        }
        if (days.isEmpty()) {
            return allDaysCsv();
        }
        List<DayOfWeek> ordered = new ArrayList<>(days);
        ordered.sort(Comparator.comparingInt(DayOfWeek::getValue));
        return ordered.stream().map(DayOfWeek::name).reduce((a, b) -> a + "," + b).orElse(allDaysCsv());
    }

    private DayOfWeek parseDay(String token) {
        String normalized = trimToNull(token);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private String allDaysCsv() {
        return "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
    }

    private void validateDateWindow(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildId() {
        return "SUBL_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private CustomerSubscriptionLineResponse toResponse(CustomerSubscriptionLineEntity row) {
        return CustomerSubscriptionLineResponse.builder()
                .subscriptionLineId(row.getSubscriptionLineId())
                .customerId(row.getCustomerId())
                .taskShift(row.getTaskShift())
                .productType(row.getProductType())
                .quantity(row.getQuantity())
                .unitPrice(row.getUnitPrice())
                .preferredTime(row.getPreferredTime())
                .activeDaysCsv(row.getActiveDaysCsv())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .active(row.isActive())
                .notes(row.getNotes())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }
}
