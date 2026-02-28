package net.nani.dairy.auth;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.dto.AuthUserResponse;
import net.nani.dairy.auth.dto.AuthUserAuditResponse;
import net.nani.dairy.auth.dto.ChangePasswordRequest;
import net.nani.dairy.auth.dto.CreateAuthUserRequest;
import net.nani.dairy.auth.dto.LoginRequest;
import net.nani.dairy.auth.dto.LoginResponse;
import net.nani.dairy.auth.dto.ResetPasswordRequest;
import net.nani.dairy.auth.dto.UpdateAuthUserRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final AuthUserAuditRepository authUserAuditRepository;
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

    public List<AuthUserResponse> listAssignableUsers(List<UserRole> roles) {
        List<AuthUserEntity> users;
        if (roles == null || roles.isEmpty()) {
            users = authUserRepository.findByActiveTrueOrderByUsernameAsc();
        } else {
            users = authUserRepository.findByActiveTrueAndRoleInOrderByUsernameAsc(roles);
        }
        return users.stream().map(this::toResponse).toList();
    }

    public List<AuthUserAuditResponse> listUserAudits(Integer limit) {
        int safeLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 200));
        return authUserAuditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    public AuthUserResponse createUser(CreateAuthUserRequest req, String actorUsername) {
        String username = normalizeUsername(req.getUsername());
        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        String rawPassword = validateAndNormalizePassword(req.getPassword(), "Password must be at least 6 characters");

        AuthUserEntity entity = AuthUserEntity.builder()
                .authUserId("USR_" + UUID.randomUUID().toString().substring(0, 8))
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(req.getFullName().trim())
                .role(req.getRole())
                .active(req.getActive() == null || req.getActive())
                .build();
        AuthUserEntity saved = authUserRepository.save(entity);
        logAudit(actorUsername, "CREATE_USER", saved, "role=" + saved.getRole() + ", active=" + saved.isActive());
        return toResponse(saved);
    }

    public AuthUserResponse updateUser(String userId, UpdateAuthUserRequest req, String actorUsername) {
        AuthUserEntity user = authUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserRole oldRole = user.getRole();
        boolean oldActive = user.isActive();
        ensureLastAdminSafeguard(user, req.getRole(), Boolean.TRUE.equals(req.getActive()));

        user.setFullName(req.getFullName().trim());
        user.setRole(req.getRole());
        user.setActive(Boolean.TRUE.equals(req.getActive()));

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            String rawPassword = validateAndNormalizePassword(req.getPassword(), "Password must be at least 6 characters");
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        AuthUserEntity saved = authUserRepository.save(user);
        String details = "role: " + oldRole + " -> " + saved.getRole() + ", active: " + oldActive + " -> " + saved.isActive();
        logAudit(actorUsername, "UPDATE_USER", saved, details);
        return toResponse(saved);
    }

    public AuthUserResponse deactivateUser(String userId, String actorUsername) {
        AuthUserEntity user = authUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ensureLastAdminSafeguard(user, user.getRole(), false);
        user.setActive(false);
        AuthUserEntity saved = authUserRepository.save(user);
        logAudit(actorUsername, "DEACTIVATE_USER", saved, "User deactivated via admin action");
        return toResponse(saved);
    }

    public AuthUserResponse resetPasswordByAdmin(String userId, ResetPasswordRequest req, String actorUsername) {
        AuthUserEntity user = authUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String rawPassword = validateAndNormalizePassword(req.getNewPassword(), "New password must be at least 6 characters");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        AuthUserEntity saved = authUserRepository.save(user);
        logAudit(actorUsername, "ADMIN_RESET_PASSWORD", saved, "Password reset by admin");
        return toResponse(saved);
    }

    public void changePassword(String username, ChangePasswordRequest req) {
        AuthUserEntity user = authUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        String rawPassword = validateAndNormalizePassword(req.getNewPassword(), "New password must be at least 6 characters");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        AuthUserEntity saved = authUserRepository.save(user);
        logAudit(username, "CHANGE_PASSWORD_SELF", saved, "Password changed by user");
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

    private AuthUserAuditResponse toAuditResponse(AuthUserAuditEntity row) {
        return AuthUserAuditResponse.builder()
                .auditId(row.getAuditId())
                .actorUsername(row.getActorUsername())
                .action(row.getAction())
                .targetUserId(row.getTargetUserId())
                .targetUsername(row.getTargetUsername())
                .details(row.getDetails())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private void logAudit(String actorUsername, String action, AuthUserEntity target, String details) {
        String actor = actorUsername == null || actorUsername.isBlank() ? "system" : actorUsername.trim();
        authUserAuditRepository.save(AuthUserAuditEntity.builder()
                .auditId("AUD_" + UUID.randomUUID().toString().substring(0, 8))
                .actorUsername(actor)
                .action(action)
                .targetUserId(target != null ? target.getAuthUserId() : null)
                .targetUsername(target != null ? target.getUsername() : null)
                .details(details)
                .build());
    }

    private void ensureLastAdminSafeguard(AuthUserEntity target, UserRole nextRole, boolean nextActive) {
        boolean currentlyActiveAdmin = target.getRole() == UserRole.ADMIN && target.isActive();
        boolean remainsActiveAdmin = nextRole == UserRole.ADMIN && nextActive;
        if (!currentlyActiveAdmin || remainsActiveAdmin) {
            return;
        }

        long activeAdmins = authUserRepository.countByRoleAndActive(UserRole.ADMIN, true);
        if (activeAdmins <= 1) {
            throw new IllegalArgumentException("Cannot deactivate or demote the last active ADMIN user");
        }
    }

    private String validateAndNormalizePassword(String password, String errorMessage) {
        String rawPassword = password == null ? "" : password.trim();
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException(errorMessage);
        }
        return rawPassword;
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
