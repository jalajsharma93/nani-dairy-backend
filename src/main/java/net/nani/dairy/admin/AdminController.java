package net.nani.dairy.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminSeedService adminSeedService;

    @PostMapping("/seed-mvp")
    @PreAuthorize("hasRole('ADMIN')")
    public SeedMvpResponse seedMvp() {
        return adminSeedService.seedMvp();
    }

    @PostMapping("/migrate-animal-ids")
    @PreAuthorize("hasRole('ADMIN')")
    public MigrateAnimalIdsResponse migrateAnimalIds() {
        return adminSeedService.migrateAnimalIds();
    }
}
