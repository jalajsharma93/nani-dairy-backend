package net.nani.dairy.auth.dto;

import lombok.*;
import net.nani.dairy.auth.UserRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserResponse {
    private String userId;
    private String username;
    private String fullName;
    private UserRole role;
    private boolean active;
}
