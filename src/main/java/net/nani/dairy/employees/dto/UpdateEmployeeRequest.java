package net.nani.dairy.employees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import net.nani.dairy.employees.EmployeeGovernmentIdType;
import net.nani.dairy.employees.EmployeeType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmployeeRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    @NotBlank
    private String roleTitle;

    @NotNull
    private LocalDate joinDate;

    @NotNull
    private EmployeeGovernmentIdType governmentIdType;

    @NotBlank
    private String governmentIdNumber;

    private String address;

    private String emergencyContactName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Emergency contact phone must be 10 digits")
    private String emergencyContactPhone;

    private String bankAccountNumber;

    private String ifscCode;

    @Pattern(regexp = "^[0-9]{12}$", message = "UAN must be 12 digits")
    private String uan;

    @Pattern(regexp = "^[0-9]{10}$", message = "ESIC IP number must be 10 digits")
    private String esicIpNumber;

    @NotNull
    private EmployeeType type;

    @NotNull
    private Boolean isActive;
}
