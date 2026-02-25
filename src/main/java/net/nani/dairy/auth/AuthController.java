package net.nani.dairy.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.dto.AuthUserAuditResponse;
import net.nani.dairy.auth.dto.AuthUserResponse;
import net.nani.dairy.auth.dto.ChangePasswordRequest;
import net.nani.dairy.auth.dto.CreateAuthUserRequest;
import net.nani.dairy.auth.dto.LoginRequest;
import net.nani.dairy.auth.dto.LoginResponse;
import net.nani.dairy.auth.dto.ResetPasswordRequest;
import net.nani.dairy.auth.dto.UpdateAuthUserRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public AuthUserResponse createUser(@Valid @RequestBody CreateAuthUserRequest req, Authentication authentication) {
        return authService.createUser(req, usernameFromAuth(authentication));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateAuthUserRequest req,
            Authentication authentication
    ) {
        return authService.updateUser(userId, req, usernameFromAuth(authentication));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse deactivateUser(@PathVariable String userId, Authentication authentication) {
        return authService.deactivateUser(userId, usernameFromAuth(authentication));
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse resetUserPassword(
            @PathVariable String userId,
            @Valid @RequestBody ResetPasswordRequest req,
            Authentication authentication
    ) {
        return authService.resetPasswordByAdmin(userId, req, usernameFromAuth(authentication));
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        authService.changePassword(usernameFromAuth(authentication), req);
    }

    @GetMapping("/users/audits")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuthUserAuditResponse> listUserAudits(@RequestParam(required = false) Integer limit) {
        return authService.listUserAudits(limit);
    }

    private String usernameFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return authentication.getName();
    }
}
