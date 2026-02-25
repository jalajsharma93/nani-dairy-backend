package net.nani.dairy.employees;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.CreateEmployeeRequest;
import net.nani.dairy.employees.dto.EmployeeResponse;
import net.nani.dairy.employees.dto.UpdateEmployeeRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository repo;

    public List<EmployeeResponse> list(Boolean active, EmployeeType type) {
        List<EmployeeEntity> employees;

        if (active != null && type != null) {
            employees = repo.findByIsActiveAndType(active, type);
        } else if (active != null) {
            employees = repo.findByIsActive(active);
        } else if (type != null) {
            employees = repo.findByType(type);
        } else {
            employees = repo.findAll();
        }

        return employees.stream().map(this::toResponse).toList();
    }

    public EmployeeResponse create(CreateEmployeeRequest req) {
        var entity = EmployeeEntity.builder()
                .employeeId(buildId())
                .name(req.getName().trim())
                .phone(req.getPhone().trim())
                .roleTitle(req.getRoleTitle().trim())
                .joinDate(req.getJoinDate())
                .governmentIdType(req.getGovernmentIdType())
                .governmentIdNumber(req.getGovernmentIdNumber().trim().toUpperCase())
                .address(normalizeOptional(req.getAddress()))
                .emergencyContactName(normalizeOptional(req.getEmergencyContactName()))
                .emergencyContactPhone(normalizeOptional(req.getEmergencyContactPhone()))
                .bankAccountNumber(normalizeOptional(req.getBankAccountNumber()))
                .ifscCode(normalizeOptional(req.getIfscCode(), true))
                .uan(normalizeOptional(req.getUan()))
                .esicIpNumber(normalizeOptional(req.getEsicIpNumber()))
                .type(req.getType())
                .isActive(req.getIsActive())
                .build();

        return toResponse(repo.save(entity));
    }

    public EmployeeResponse update(String employeeId, UpdateEmployeeRequest req) {
        var entity = repo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        entity.setName(req.getName().trim());
        entity.setPhone(req.getPhone().trim());
        entity.setRoleTitle(req.getRoleTitle().trim());
        entity.setJoinDate(req.getJoinDate());
        entity.setGovernmentIdType(req.getGovernmentIdType());
        entity.setGovernmentIdNumber(req.getGovernmentIdNumber().trim().toUpperCase());
        entity.setAddress(normalizeOptional(req.getAddress()));
        entity.setEmergencyContactName(normalizeOptional(req.getEmergencyContactName()));
        entity.setEmergencyContactPhone(normalizeOptional(req.getEmergencyContactPhone()));
        entity.setBankAccountNumber(normalizeOptional(req.getBankAccountNumber()));
        entity.setIfscCode(normalizeOptional(req.getIfscCode(), true));
        entity.setUan(normalizeOptional(req.getUan()));
        entity.setEsicIpNumber(normalizeOptional(req.getEsicIpNumber()));
        entity.setType(req.getType());
        entity.setActive(req.getIsActive());

        return toResponse(repo.save(entity));
    }

    private String buildId() {
        return "EMP_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private EmployeeResponse toResponse(EmployeeEntity e) {
        return EmployeeResponse.builder()
                .employeeId(e.getEmployeeId())
                .name(e.getName())
                .phone(e.getPhone())
                .roleTitle(e.getRoleTitle())
                .joinDate(e.getJoinDate())
                .governmentIdType(e.getGovernmentIdType())
                .governmentIdNumber(e.getGovernmentIdNumber())
                .address(e.getAddress())
                .emergencyContactName(e.getEmergencyContactName())
                .emergencyContactPhone(e.getEmergencyContactPhone())
                .bankAccountNumber(e.getBankAccountNumber())
                .ifscCode(e.getIfscCode())
                .uan(e.getUan())
                .esicIpNumber(e.getEsicIpNumber())
                .type(e.getType())
                .isActive(e.isActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private String normalizeOptional(String value) {
        return normalizeOptional(value, false);
    }

    private String normalizeOptional(String value, boolean uppercase) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return uppercase ? trimmed.toUpperCase() : trimmed;
    }
}
