package net.nani.dairy.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthUserSeeder.class);

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedUser("admin", "NANI Admin", UserRole.ADMIN, "admin123");
        seedUser("manager", "NANI Manager", UserRole.MANAGER, "manager123");
        seedUser("worker", "NANI Worker", UserRole.WORKER, "worker123");
        safeSeedUser("feedmanager", "NANI Feed Manager", UserRole.FEED_MANAGER, "feed123");
        safeSeedUser("delivery", "NANI Delivery", UserRole.DELIVERY, "delivery123");
        safeSeedUser("vet", "NANI Vet", UserRole.VET, "vet123");
    }

    private void safeSeedUser(String username, String fullName, UserRole role, String rawPassword) {
        try {
            seedUser(username, fullName, role, rawPassword);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Skipping {} seed on existing DB schema. Reset DB or migrate enum to include newer roles.", role);
        }
    }

    private void seedUser(String username, String fullName, UserRole role, String rawPassword) {
        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        authUserRepository.save(AuthUserEntity.builder()
                .authUserId("USR_" + UUID.randomUUID().toString().substring(0, 8))
                .username(username)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(true)
                .build());
    }
}
