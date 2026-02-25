package net.nani.dairy.auth;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.dto.AuthUserResponse;
import net.nani.dairy.auth.dto.CreateAuthUserRequest;
import net.nani.dairy.auth.dto.LoginRequest;
import net.nani.dairy.auth.dto.LoginResponse;
import net.nani.dairy.auth.dto.UpdateAuthUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest req) {
        String username = req.getUsername().trim();

        AuthUserEntity user = authUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("User is inactive");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return LoginResponse.builder()
                .token(jwtService.generateToken(user))
                .tokenType("Bearer")
                .expiresAt(jwtService.getTokenExpiryFromNow())
                .user(toResponse(user))
                .build();
    }

    public AuthUserResponse me(String username) {
        AuthUserEntity user = authUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(user);
    }

    public List<AuthUserResponse> listUsers() {
        return authUserRepository.findAll()
                .stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    public AuthUserResponse createUser(CreateAuthUserRequest req) {
        String username = normalizeUsername(req.getUsername());
        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        String rawPassword = req.getPassword() == null ? "" : req.getPassword().trim();
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        AuthUserEntity entity = AuthUserEntity.builder()
                .authUserId("USR_" + UUID.randomUUID().toString().substring(0, 8))
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(req.getFullName().trim())
                .role(req.getRole())
                .active(req.getActive() == null || req.getActive())
                .build();

        return toResponse(authUserRepository.save(entity));
    }

    public AuthUserResponse updateUser(String userId, UpdateAuthUserRequest req) {
        AuthUserEntity user = authUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setFullName(req.getFullName().trim());
        user.setRole(req.getRole());
        user.setActive(Boolean.TRUE.equals(req.getActive()));

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            String rawPassword = req.getPassword().trim();
            if (rawPassword.length() < 6) {
                throw new IllegalArgumentException("Password must be at least 6 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }

        return toResponse(authUserRepository.save(user));
    }

    private AuthUserResponse toResponse(AuthUserEntity user) {
        return AuthUserResponse.builder()
                .userId(user.getAuthUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    private String normalizeUsername(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        return normalized;
    }
}
