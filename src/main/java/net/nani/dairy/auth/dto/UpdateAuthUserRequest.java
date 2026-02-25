package net.nani.dairy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class UpdateAuthUserRequest {

    @NotBlank
    private String fullName;

    @NotNull
    private UserRole role;

    @NotNull
    private Boolean active;

    private String password;
}
