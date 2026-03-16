package net.nani.dairy.audit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApprovalRequest {

    @NotBlank
    private String module;

    @NotBlank
    private String actionType;

    private String targetRefId;

    private UserRole requiredApproverRole;

    @NotBlank
    private String requestReason;

    private String requestPayloadJson;
}
