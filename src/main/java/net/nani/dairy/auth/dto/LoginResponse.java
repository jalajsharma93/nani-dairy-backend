package net.nani.dairy.auth.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;
    private OffsetDateTime expiresAt;
    private AuthUserResponse user;
}
