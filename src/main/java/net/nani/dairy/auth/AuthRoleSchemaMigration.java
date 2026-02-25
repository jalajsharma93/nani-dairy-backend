package net.nani.dairy.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
public class AuthRoleSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthRoleSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE auth_user ALTER COLUMN role VARCHAR(32)");
            log.info("Auth role column migrated to VARCHAR(32)");
        } catch (DataAccessException ex) {
            // Keep startup resilient across fresh DB/first boot edge cases.
            log.warn("Auth role column migration skipped: {}", ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage());
        }
    }
}
