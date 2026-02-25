package net.nani.dairy.admin;

public record MigrateAnimalIdsResponse(
        int migratedCount,
        long totalAnimals
) {
}
