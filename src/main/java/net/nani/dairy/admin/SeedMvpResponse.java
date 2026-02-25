package net.nani.dairy.admin;

public record SeedMvpResponse(
        int animalsAdded,
        int employeesAdded,
        int milkBatchesAdded,
        long totalAnimals,
        long totalEmployees,
        long totalMilkBatches
) {
}
