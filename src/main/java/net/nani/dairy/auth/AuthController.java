package net.nani.dairy.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.dto.AuthUserResponse;
import net.nani.dairy.auth.dto.CreateAuthUserRequest;
import net.nani.dairy.auth.dto.LoginRequest;
import net.nani.dairy.auth.dto.LoginResponse;
import net.nani.dairy.auth.dto.UpdateAuthUserRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return authService.me(authentication.getName());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuthUserResponse> listUsers() {
        return authService.listUsers();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse createUser(@Valid @RequestBody CreateAuthUserRequest req) {
        return authService.createUser(req);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateAuthUserRequest req
    ) {
        return authService.updateUser(userId, req);
    }
}
