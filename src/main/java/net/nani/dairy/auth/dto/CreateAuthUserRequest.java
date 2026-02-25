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
public class CreateAuthUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String fullName;

    @NotNull
    private UserRole role;

    @NotBlank
    private String password;

    private Boolean active;
}
