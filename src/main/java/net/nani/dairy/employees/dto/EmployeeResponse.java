package net.nani.dairy.employees.dto;

import lombok.*;
import net.nani.dairy.employees.EmployeeGovernmentIdType;
import net.nani.dairy.employees.EmployeeType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {
    private String employeeId;
    private String name;
    private String phone;
    private String roleTitle;
    private LocalDate joinDate;
    private EmployeeGovernmentIdType governmentIdType;
    private String governmentIdNumber;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String bankAccountNumber;
    private String ifscCode;
    private String uan;
    private String esicIpNumber;
    private EmployeeType type;
    private boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
