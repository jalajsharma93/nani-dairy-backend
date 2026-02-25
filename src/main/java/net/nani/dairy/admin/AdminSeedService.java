package net.nani.dairy.admin;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.employees.EmployeeEntity;
import net.nani.dairy.employees.EmployeeGovernmentIdType;
import net.nani.dairy.employees.EmployeeRepository;
import net.nani.dairy.employees.EmployeeType;
import net.nani.dairy.expenses.ExpenseCategory;
import net.nani.dairy.expenses.ExpenseEntity;
import net.nani.dairy.expenses.ExpensePaymentMode;
import net.nani.dairy.expenses.ExpenseRepository;
import net.nani.dairy.feed.FeedLogEntity;
import net.nani.dairy.feed.FeedLogRepository;
import net.nani.dairy.feed.FeedMaterialCategory;
import net.nani.dairy.feed.FeedMaterialEntity;
import net.nani.dairy.feed.FeedMaterialRepository;
import net.nani.dairy.feed.FeedMaterialUnit;
import net.nani.dairy.feed.FeedRationPhase;
import net.nani.dairy.feed.FeedRecipeEntity;
import net.nani.dairy.feed.FeedRecipeRepository;
import net.nani.dairy.feed.FeedSopTaskEntity;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskRepository;
import net.nani.dairy.feed.FeedSopTaskStatus;
import net.nani.dairy.health.BreedingCalfGender;
import net.nani.dairy.health.BreedingCalvingOutcome;
import net.nani.dairy.health.BreedingEventEntity;
import net.nani.dairy.health.BreedingEventRepository;
import net.nani.dairy.health.BreedingPregnancyResult;
import net.nani.dairy.health.DewormingEntity;
import net.nani.dairy.health.DewormingRepository;
import net.nani.dairy.health.MedicalTreatmentEntity;
import net.nani.dairy.health.MedicalTreatmentRepository;
import net.nani.dairy.health.VaccinationEntity;
import net.nani.dairy.health.VaccinationRepository;
import net.nani.dairy.milk.MilkBatchEntity;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.milk.MilkEntryEntity;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.CustomerRecordEntity;
import net.nani.dairy.sales.CustomerRecordRepository;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.PaymentMode;
import net.nani.dairy.sales.PaymentStatus;
import net.nani.dairy.sales.ProductType;
import net.nani.dairy.sales.SaleEntity;
import net.nani.dairy.sales.SaleRepository;
import net.nani.dairy.sales.SettlementCycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AdminSeedService {

    private static final DateTimeFormatter ANIMAL_ID_TS = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final DateTimeFormatter DAY_TOKEN = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TAG_GIR = "TST-GIR-101";
    private static final String TAG_SAI = "TST-SAI-102";
    private static final String TAG_BUF = "TST-BUF-201";
    private static final String TAG_CALF = "TST-CALF-045";
    private static final String TAG_SICK = "TST-SICK-301";

    private final AnimalRepository animalRepository;
    private final EmployeeRepository employeeRepository;
    private final MilkBatchRepository milkBatchRepository;
    private final MilkEntryRepository milkEntryRepository;
    private final VaccinationRepository vaccinationRepository;
    private final DewormingRepository dewormingRepository;
    private final BreedingEventRepository breedingEventRepository;
    private final MedicalTreatmentRepository medicalTreatmentRepository;
    private final FeedLogRepository feedLogRepository;
    private final FeedMaterialRepository feedMaterialRepository;
    private final FeedRecipeRepository feedRecipeRepository;
    private final FeedSopTaskRepository feedSopTaskRepository;
    private final CustomerRecordRepository customerRecordRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public SeedMvpResponse seedMvp() {
        migrateLegacyAnimalIds();
        LocalDate today = LocalDate.now();

        int animalsAdded = seedAnimals(today);
        int employeesAdded = seedEmployees(today);
        int milkBatchesAdded = seedMilkBatches(today);

        Map<String, String> animalIds = resolveSeedAnimalIds();
        seedMilkEntries(today, animalIds);
        seedHealthRecords(today, animalIds);
        seedFeedData(today, animalIds);
        seedCustomersAndSales(today);
        seedExpenses(today);

        return new SeedMvpResponse(
                animalsAdded,
                employeesAdded,
                milkBatchesAdded,
                animalRepository.count(),
                employeeRepository.count(),
                milkBatchRepository.count()
        );
    }

    @Transactional
    public MigrateAnimalIdsResponse migrateAnimalIds() {
        int migrated = migrateLegacyAnimalIds();
        return new MigrateAnimalIdsResponse(migrated, animalRepository.count());
    }

    private int seedAnimals(LocalDate today) {
        int created = 0;
        created += saveAnimalIfMissing(
                TAG_GIR, "Gauri", "Gir", AnimalStatus.LACTATING, true,
                null, "BULL-GIR-11", today.minusYears(5), AnimalGrowthStage.ADULT,
                28.0, 468.0, today.minusDays(12), null, null
        );
        created += saveAnimalIfMissing(
                TAG_SAI, "Sita", "Sahiwal", AnimalStatus.LACTATING, true,
                null, "BULL-SAI-04", today.minusYears(4), AnimalGrowthStage.ADULT,
                30.0, 442.0, today.minusDays(14), null, null
        );
        created += saveAnimalIfMissing(
                TAG_BUF, "Kamdhenu", "Buffalo", AnimalStatus.DRY, true,
                null, "BULL-BUF-03", today.minusYears(6), AnimalGrowthStage.ADULT,
                35.0, 512.0, today.minusDays(20), null, null
        );
        created += saveAnimalIfMissing(
                TAG_SICK, "Ishwari", "Gir", AnimalStatus.SICK, true,
                null, "BULL-GIR-08", today.minusYears(3), AnimalGrowthStage.ADULT,
                27.5, 398.0, today.minusDays(7), null, null
        );

        String motherAnimalId = animalRepository.findByTag(TAG_GIR).map(AnimalEntity::getAnimalId).orElse(null);
        created += saveAnimalIfMissing(
                TAG_CALF, "Choti", "Gir", AnimalStatus.DRY, true,
                motherAnimalId, "BULL-GIR-11", today.minusMonths(10), AnimalGrowthStage.CALF,
                24.0, 152.0, today.minusDays(8), today.minusMonths(4), 96.0
        );
        return created;
    }

    private int saveAnimalIfMissing(
            String tag,
            String name,
            String breed,
            AnimalStatus status,
            boolean isActive,
            String motherAnimalId,
            String sireTag,
            LocalDate dateOfBirth,
            AnimalGrowthStage growthStage,
            Double birthWeightKg,
            Double currentWeightKg,
            LocalDate lastWeightDate,
            LocalDate weaningDate,
            Double weaningWeightKg
    ) {
        if (animalRepository.existsByTag(tag)) {
            return 0;
        }

        animalRepository.save(AnimalEntity.builder()
                .animalId(buildAnimalId(breed))
                .tag(tag)
                .name(name)
                .breed(breed)
                .status(status)
                .isActive(isActive)
                .motherAnimalId(motherAnimalId)
                .sireTag(sireTag)
                .dateOfBirth(dateOfBirth)
                .growthStage(growthStage)
                .birthWeightKg(birthWeightKg)
                .currentWeightKg(currentWeightKg)
                .lastWeightDate(lastWeightDate)
                .weaningDate(weaningDate)
                .weaningWeightKg(weaningWeightKg)
                .build());
        return 1;
    }

    private int seedEmployees(LocalDate today) {
        int created = 0;
        created += saveEmployeeIfMissing(EmployeeEntity.builder()
                .employeeId("EMP_TST_001")
                .name("Ramesh Yadav")
                .phone("9876543210")
                .roleTitle("Milker")
                .joinDate(today.minusYears(2))
                .governmentIdType(EmployeeGovernmentIdType.AADHAAR)
                .governmentIdNumber("123412341234")
                .address("Village Rampur, Jaipur, Rajasthan")
                .emergencyContactName("Sita Yadav")
                .emergencyContactPhone("9876500011")
                .bankAccountNumber("123456789012")
                .ifscCode("SBIN0001234")
                .uan("100200300400")
                .esicIpNumber("110022003300")
                .type(EmployeeType.FULL_TIME)
                .isActive(true)
                .build());
        created += saveEmployeeIfMissing(EmployeeEntity.builder()
                .employeeId("EMP_TST_002")
                .name("Kiran Devi")
                .phone("9988776655")
                .roleTitle("Cleaner")
                .joinDate(today.minusMonths(16))
                .governmentIdType(EmployeeGovernmentIdType.VOTER_ID)
                .governmentIdNumber("RJ/12/998811")
                .address("Village Khejri, Jaipur, Rajasthan")
                .emergencyContactName("Mohan Lal")
                .emergencyContactPhone("9988776600")
                .bankAccountNumber("202020202020")
                .ifscCode("PUNB0123400")
                .type(EmployeeType.PART_TIME)
                .isActive(true)
                .build());
        created += saveEmployeeIfMissing(EmployeeEntity.builder()
                .employeeId("EMP_TST_003")
                .name("Vikram Singh")
                .phone("9123456789")
                .roleTitle("Feed Supervisor")
                .joinDate(today.minusMonths(20))
                .governmentIdType(EmployeeGovernmentIdType.PAN)
                .governmentIdNumber("ABCDE1234F")
                .address("Ward 9, Dairy Road, Jaipur")
                .emergencyContactName("Anita Singh")
                .emergencyContactPhone("9123456700")
                .bankAccountNumber("303030303030")
                .ifscCode("HDFC0001122")
                .type(EmployeeType.FULL_TIME)
                .isActive(true)
                .build());
        created += saveEmployeeIfMissing(EmployeeEntity.builder()
                .employeeId("EMP_TST_004")
                .name("Driver Omprakash")
                .phone("9012345678")
                .roleTitle("Delivery")
                .joinDate(today.minusMonths(9))
                .governmentIdType(EmployeeGovernmentIdType.DRIVING_LICENSE)
                .governmentIdNumber("RJ1420200011223")
                .address("Transport Nagar, Jaipur")
                .emergencyContactName("Rekha")
                .emergencyContactPhone("9012345600")
                .bankAccountNumber("404040404040")
                .ifscCode("ICIC0005678")
                .type(EmployeeType.PART_TIME)
                .isActive(true)
                .build());
        return created;
    }

    private int saveEmployeeIfMissing(EmployeeEntity entity) {
        if (employeeRepository.existsById(entity.getEmployeeId())) {
            return 0;
        }
        employeeRepository.save(entity);
        return 1;
    }

    private int seedMilkBatches(LocalDate today) {
        int created = 0;
        created += upsertMilkBatch(today.minusDays(2), Shift.AM, 175.10, QcStatus.HOLD);
        created += upsertMilkBatch(today.minusDays(2), Shift.PM, 162.35, QcStatus.REJECT);
        created += upsertMilkBatch(today.minusDays(1), Shift.AM, 178.40, QcStatus.PASS);
        created += upsertMilkBatch(today.minusDays(1), Shift.PM, 165.85, QcStatus.PASS);
        created += upsertMilkBatch(today, Shift.AM, 182.25, QcStatus.PASS);
        created += upsertMilkBatch(today, Shift.PM, 171.10, QcStatus.PENDING);
        return created;
    }

    private int upsertMilkBatch(LocalDate date, Shift shift, double liters, QcStatus qcStatus) {
        var existing = milkBatchRepository.findByDateAndShift(date, shift);
        if (existing.isPresent()) {
            MilkBatchEntity batch = existing.get();
            batch.setTotalLiters(liters);
            batch.setQcStatus(qcStatus);
            milkBatchRepository.save(batch);
            return 0;
        }

        milkBatchRepository.save(MilkBatchEntity.builder()
                .milkBatchId("MB_TST_" + dayToken(date) + "_" + shift)
                .date(date)
                .shift(shift)
                .totalLiters(liters)
                .qcStatus(qcStatus)
                .build());
        return 1;
    }

    private void seedMilkEntries(LocalDate today, Map<String, String> animalIds) {
        String girId = animalIds.get(TAG_GIR);
        String saiId = animalIds.get(TAG_SAI);
        String bufId = animalIds.get(TAG_BUF);
        if (girId == null || saiId == null || bufId == null) {
            return;
        }

        upsertMilkEntry(today.minusDays(1), Shift.AM, girId, 14.8, QcStatus.PASS, 4.2, 8.6, 26.0, 30.5, "Clean", null);
        upsertMilkEntry(today.minusDays(1), Shift.AM, saiId, 13.6, QcStatus.PASS, 3.9, 8.4, 26.2, 29.8, "Clean", null);
        upsertMilkEntry(today.minusDays(1), Shift.AM, bufId, 10.1, QcStatus.PASS, 6.8, 9.0, 26.0, 31.0, "Good", null);

        upsertMilkEntry(today.minusDays(2), Shift.AM, girId, 13.2, QcStatus.HOLD, 3.1, 7.9, 33.2, 27.1, "Warm smell", "High temperature, retest required");
        upsertMilkEntry(today.minusDays(2), Shift.AM, saiId, 12.4, QcStatus.HOLD, 3.3, 8.0, 32.5, 27.4, "Borderline", "Suspected transport delay");
        upsertMilkEntry(today.minusDays(2), Shift.PM, bufId, 9.1, QcStatus.REJECT, 2.8, 7.5, 35.1, 26.8, "Sour", "Failed fat and SNF threshold");

        upsertMilkEntry(today, Shift.AM, girId, 15.1, QcStatus.PASS, 4.3, 8.6, 25.8, 30.7, "Good", null);
        upsertMilkEntry(today, Shift.AM, saiId, 13.9, QcStatus.PASS, 4.0, 8.5, 26.0, 30.1, "Good", null);
        upsertMilkEntry(today, Shift.AM, bufId, 10.4, QcStatus.PASS, 6.9, 9.1, 25.9, 31.2, "Good", null);

        upsertMilkEntry(today, Shift.PM, girId, 13.7, QcStatus.PENDING, null, null, null, null, null, null);
        upsertMilkEntry(today, Shift.PM, saiId, 12.9, QcStatus.PENDING, null, null, null, null, null, null);
        upsertMilkEntry(today, Shift.PM, bufId, 9.8, QcStatus.PENDING, null, null, null, null, null, null);
    }

    private void upsertMilkEntry(
            LocalDate date,
            Shift shift,
            String animalId,
            double liters,
            QcStatus qcStatus,
            Double fat,
            Double snf,
            Double temperature,
            Double lactometer,
            String smellNotes,
            String rejectionReason
    ) {
        var existing = milkEntryRepository.findByDateAndShiftAndAnimalId(date, shift, animalId);
        MilkEntryEntity row = existing.orElseGet(() -> MilkEntryEntity.builder()
                .milkEntryId("ME_TST_" + dayToken(date) + "_" + shift + "_" + compactToken(animalId, 12))
                .date(date)
                .shift(shift)
                .animalId(animalId)
                .build());

        row.setLiters(liters);
        row.setQcStatus(qcStatus);
        row.setFat(fat);
        row.setSnf(snf);
        row.setTemperature(temperature);
        row.setLactometer(lactometer);
        row.setSmellNotes(smellNotes);
        row.setRejectionReason(rejectionReason);
        milkEntryRepository.save(row);
    }

    private void seedHealthRecords(LocalDate today, Map<String, String> animalIds) {
        String girId = animalIds.get(TAG_GIR);
        String saiId = animalIds.get(TAG_SAI);
        String calfId = animalIds.get(TAG_CALF);
        String sickId = animalIds.get(TAG_SICK);
        if (girId == null || saiId == null || calfId == null || sickId == null) {
            return;
        }

        upsertVaccination(VaccinationEntity.builder()
                .vaccinationId("VAC_TST_GIR_FMD")
                .animalId(girId)
                .vaccineName("FMD")
                .diseaseTarget("Foot and Mouth Disease")
                .doseDate(today.minusDays(14))
                .doseNumber(2)
                .boosterDueDate(today.plusDays(170))
                .nextDueDate(today.plusDays(166))
                .vaccineExpiryDate(today.plusDays(180))
                .batchLotNo("LOT-FMD-2026-011")
                .route("IM")
                .notes("Routine FMD vaccination completed.")
                .build());

        upsertVaccination(VaccinationEntity.builder()
                .vaccinationId("VAC_TST_SAI_HS")
                .animalId(saiId)
                .vaccineName("HS")
                .diseaseTarget("Haemorrhagic Septicaemia")
                .doseDate(today.minusDays(370))
                .doseNumber(1)
                .nextDueDate(today.minusDays(5))
                .vaccineExpiryDate(today.minusDays(2))
                .batchLotNo("LOT-HS-2025-104")
                .route("SC")
                .notes("Overdue sample for alert testing.")
                .build());

        upsertVaccination(VaccinationEntity.builder()
                .vaccinationId("VAC_TST_CALF_BRU")
                .animalId(calfId)
                .vaccineName("Brucellosis")
                .diseaseTarget("Bovine Brucellosis")
                .doseDate(today.minusDays(40))
                .doseNumber(1)
                .nextDueDate(null)
                .route("SC")
                .notes("Single-dose calf vaccination.")
                .build());

        upsertDeworming(DewormingEntity.builder()
                .dewormingId("DWRM_TST_GIR")
                .animalId(girId)
                .drugName("Albendazole")
                .doseDate(today.minusDays(95))
                .nextDueDate(today.plusDays(5))
                .weightAtDoseKg(468.0)
                .notes("Routine quarterly deworming.")
                .build());

        upsertDeworming(DewormingEntity.builder()
                .dewormingId("DWRM_TST_SAI")
                .animalId(saiId)
                .drugName("Fenbendazole")
                .doseDate(today.minusDays(215))
                .nextDueDate(today.minusDays(8))
                .weightAtDoseKg(442.0)
                .notes("Overdue sample record for dashboard testing.")
                .build());

        upsertBreedingEvent(BreedingEventEntity.builder()
                .breedingEventId("BRD_TST_SAI_OPEN")
                .animalId(saiId)
                .heatDate(today.minusDays(285))
                .inseminationDate(today.minusDays(276))
                .sireTag("BULL-SAI-04")
                .pregnancyCheckDate(today.minusDays(220))
                .pregnancyResult(BreedingPregnancyResult.PREGNANT)
                .expectedCalvingDate(today.plusDays(7))
                .actualCalvingDate(null)
                .calfAnimalId(null)
                .calfTag(null)
                .calfGender(BreedingCalfGender.UNKNOWN)
                .calvingOutcome(BreedingCalvingOutcome.UNKNOWN)
                .notes("Expected calving in next 7 days.")
                .build());

        upsertBreedingEvent(BreedingEventEntity.builder()
                .breedingEventId("BRD_TST_GIR_REPEAT")
                .animalId(girId)
                .heatDate(today.minusDays(72))
                .inseminationDate(today.minusDays(66))
                .sireTag("BULL-GIR-11")
                .pregnancyCheckDate(today.minusDays(32))
                .pregnancyResult(BreedingPregnancyResult.NOT_PREGNANT)
                .expectedCalvingDate(null)
                .actualCalvingDate(null)
                .calfAnimalId(null)
                .calfTag(null)
                .calfGender(BreedingCalfGender.UNKNOWN)
                .calvingOutcome(BreedingCalvingOutcome.UNKNOWN)
                .notes("Repeat breeder case sample.")
                .build());

        upsertMedicalTreatment(MedicalTreatmentEntity.builder()
                .treatmentId("TRT_TST_GIR_MAST")
                .animalId(girId)
                .treatmentDate(today.minusDays(3))
                .diagnosis("Mastitis")
                .medicineName("Ceftriaxone")
                .dose("10 ml")
                .route("IM")
                .veterinarianName("Dr. Meena")
                .prescriptionPhotoUrl("https://example.com/rx/gir-mastitis.jpg")
                .withdrawalTillDate(today.plusDays(4))
                .followUpDate(today.plusDays(2))
                .notes("Observe udder swelling and temperature.")
                .build());

        upsertMedicalTreatment(MedicalTreatmentEntity.builder()
                .treatmentId("TRT_TST_SICK_FEVER")
                .animalId(sickId)
                .treatmentDate(today.minusDays(1))
                .diagnosis("Fever")
                .medicineName("Meloxicam")
                .dose("8 ml")
                .route("IM")
                .veterinarianName("Dr. S. Verma")
                .prescriptionPhotoUrl("https://example.com/rx/sick-fever.jpg")
                .withdrawalTillDate(today.plusDays(2))
                .followUpDate(today.plusDays(1))
                .notes("Keep hydration high and monitor appetite.")
                .build());
    }

    private void seedFeedData(LocalDate today, Map<String, String> animalIds) {
        upsertFeedMaterial(FeedMaterialEntity.builder()
                .feedMaterialId("FM_TST_GREEN_01")
                .materialName("Maize Silage")
                .category(FeedMaterialCategory.GREEN_FODDER)
                .unit(FeedMaterialUnit.KG)
                .availableQty(1820)
                .reorderLevelQty(500)
                .costPerUnit(2.8)
                .supplierName("Sharma Fodder Supplier")
                .notes("Stored in pit no. 2")
                .build());
        upsertFeedMaterial(FeedMaterialEntity.builder()
                .feedMaterialId("FM_TST_DRY_01")
                .materialName("Wheat Straw")
                .category(FeedMaterialCategory.DRY_FODDER)
                .unit(FeedMaterialUnit.KG)
                .availableQty(920)
                .reorderLevelQty(350)
                .costPerUnit(8.2)
                .supplierName("Village Aggregator")
                .notes("Dry storage shed A")
                .build());
        upsertFeedMaterial(FeedMaterialEntity.builder()
                .feedMaterialId("FM_TST_CONC_01")
                .materialName("Dairy Concentrate 20%")
                .category(FeedMaterialCategory.CONCENTRATE)
                .unit(FeedMaterialUnit.BAG)
                .availableQty(42)
                .reorderLevelQty(20)
                .costPerUnit(1280.0)
                .supplierName("Rajasthan Feed Mills")
                .notes("50 kg bags")
                .build());
        upsertFeedMaterial(FeedMaterialEntity.builder()
                .feedMaterialId("FM_TST_MIN_01")
                .materialName("Mineral Mixture")
                .category(FeedMaterialCategory.MINERAL)
                .unit(FeedMaterialUnit.KG)
                .availableQty(120)
                .reorderLevelQty(40)
                .costPerUnit(92.0)
                .supplierName("Vet Nutrition")
                .notes("Use in lactating ration")
                .build());

        upsertFeedRecipe(FeedRecipeEntity.builder()
                .feedRecipeId("FR_TST_LACT")
                .recipeName("Lactating Morning Mix")
                .rationPhase(FeedRationPhase.LACTATING)
                .targetAnimalCount(12)
                .ingredients("Maize Silage 18kg + Wheat Straw 3kg + Concentrate 5kg + Mineral 120g")
                .instructions("Mix silage and straw first, add concentrate at feeding point.")
                .active(true)
                .build());
        upsertFeedRecipe(FeedRecipeEntity.builder()
                .feedRecipeId("FR_TST_DRY")
                .recipeName("Dry Cow Maintenance Mix")
                .rationPhase(FeedRationPhase.DRY)
                .targetAnimalCount(6)
                .ingredients("Silage 8kg + Straw 4kg + Concentrate 1.5kg")
                .instructions("Feed after water cycle; avoid overfeeding concentrate.")
                .active(true)
                .build());
        upsertFeedRecipe(FeedRecipeEntity.builder()
                .feedRecipeId("FR_TST_CALF")
                .recipeName("Calf Grower Starter")
                .rationPhase(FeedRationPhase.CALF)
                .targetAnimalCount(4)
                .ingredients("Calf starter 1.8kg + Green fodder 3kg")
                .instructions("Split into two meals and keep clean drinking water.")
                .active(true)
                .build());

        upsertFeedTask(FeedSopTaskEntity.builder()
                .feedTaskId("FT_TST_PREP_01")
                .taskDate(today)
                .title("Prepare lactating ration")
                .details("Use FR_TST_LACT recipe for AM shift.")
                .assignedRole(UserRole.FEED_MANAGER)
                .priority(FeedSopTaskPriority.HIGH)
                .status(FeedSopTaskStatus.IN_PROGRESS)
                .dueTime(LocalTime.of(5, 30))
                .completedAt(null)
                .completedBy(null)
                .build());
        upsertFeedTask(FeedSopTaskEntity.builder()
                .feedTaskId("FT_TST_DISTR_01")
                .taskDate(today)
                .title("Distribute batch feed to lactating herd")
                .details("Record per-group quantities after distribution.")
                .assignedRole(UserRole.WORKER)
                .priority(FeedSopTaskPriority.HIGH)
                .status(FeedSopTaskStatus.PENDING)
                .dueTime(LocalTime.of(6, 15))
                .completedAt(null)
                .completedBy(null)
                .build());
        upsertFeedTask(FeedSopTaskEntity.builder()
                .feedTaskId("FT_TST_STOCK_01")
                .taskDate(today)
                .title("Update feed stock closing balance")
                .details("Update material-wise stock and reorder alerts.")
                .assignedRole(UserRole.MANAGER)
                .priority(FeedSopTaskPriority.MEDIUM)
                .status(FeedSopTaskStatus.DONE)
                .dueTime(LocalTime.of(19, 0))
                .completedAt(LocalDateTime.of(today, LocalTime.of(18, 45)))
                .completedBy("manager")
                .build());

        String girId = animalIds.get(TAG_GIR);
        String saiId = animalIds.get(TAG_SAI);
        String bufId = animalIds.get(TAG_BUF);
        if (girId != null) {
            upsertFeedLog(feedLog(today, girId, "Green Fodder + Concentrate", FeedRationPhase.LACTATING, 24.5, "AM mixed ration"));
            upsertFeedLog(feedLog(today.minusDays(1), girId, "Green Fodder + Concentrate", FeedRationPhase.LACTATING, 23.9, "Previous day"));
        }
        if (saiId != null) {
            upsertFeedLog(feedLog(today, saiId, "Green Fodder + Concentrate", FeedRationPhase.LACTATING, 22.8, "AM mixed ration"));
        }
        if (bufId != null) {
            upsertFeedLog(feedLog(today, bufId, "Dry Cow Mix", FeedRationPhase.DRY, 15.2, "Dry phase ration"));
        }
    }

    private FeedLogEntity feedLog(LocalDate date, String animalId, String feedType, FeedRationPhase phase, double qty, String notes) {
        return FeedLogEntity.builder()
                .feedLogId("FL_TST_" + dayToken(date) + "_" + compactToken(animalId, 10))
                .feedDate(date)
                .animalId(animalId)
                .feedType(feedType)
                .rationPhase(phase)
                .quantityKg(qty)
                .notes(notes)
                .build();
    }

    private void seedCustomersAndSales(LocalDate today) {
        upsertCustomer(CustomerRecordEntity.builder()
                .customerId("CUST_TST_COOP_01")
                .customerName("Jaipur Milk Cooperative")
                .customerType(CustomerType.COOPERATIVE)
                .phone("0141-2451122")
                .routeName("COOP-ROUTE-A")
                .collectionPoint("Central Chilling Plant")
                .subscriptionActive(true)
                .dailySubscriptionQty(120.0)
                .isActive(true)
                .notes("Monthly rate card applies.")
                .build());
        upsertCustomer(CustomerRecordEntity.builder()
                .customerId("CUST_TST_RETAIL_01")
                .customerName("Sharma Dairy Booth")
                .customerType(CustomerType.RETAIL)
                .phone("9829012345")
                .routeName("ROUTE-1")
                .collectionPoint("Civil Lines")
                .subscriptionActive(true)
                .dailySubscriptionQty(35.0)
                .isActive(true)
                .notes("Evening delivery preferred.")
                .build());
        upsertCustomer(CustomerRecordEntity.builder()
                .customerId("CUST_TST_INDIV_01")
                .customerName("Anjali Home")
                .customerType(CustomerType.INDIVIDUAL)
                .phone("9911223344")
                .routeName("ROUTE-2")
                .collectionPoint("Vaishali Nagar")
                .subscriptionActive(true)
                .dailySubscriptionQty(2.0)
                .isActive(true)
                .notes("Morning doorstep delivery.")
                .build());
        upsertCustomer(CustomerRecordEntity.builder()
                .customerId("CUST_TST_INDIV_02")
                .customerName("Rohit Family")
                .customerType(CustomerType.INDIVIDUAL)
                .phone("9898989898")
                .routeName("ROUTE-2")
                .collectionPoint("Vaishali Nagar")
                .subscriptionActive(false)
                .dailySubscriptionQty(null)
                .isActive(true)
                .notes("On-demand orders only.")
                .build());

        upsertSale(SaleEntity.builder()
                .saleId("SALE_TST_MILK_01")
                .dispatchDate(today)
                .customerType(CustomerType.RETAIL)
                .customerName("Sharma Dairy Booth")
                .productType(ProductType.MILK)
                .quantity(36.0)
                .unitPrice(56.0)
                .baseUnitPrice(54.0)
                .routeName("ROUTE-1")
                .collectionPoint("Civil Lines")
                .fatPercent(4.1)
                .snfPercent(8.5)
                .fatRatePerKg(7.5)
                .snfRatePerKg(2.8)
                .qualityPricingApplied(true)
                .settlementCycle(SettlementCycle.DAILY)
                .reconciled(false)
                .reconciledAt(null)
                .reconciledBy(null)
                .reconciliationNote(null)
                .delivered(true)
                .deliveredAt(LocalDateTime.now().minusHours(2).atOffset(java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now())))
                .deliveredBy("delivery")
                .deliveryNote("Delivered by route vehicle RJ14-XY-7788")
                .totalAmount(2016.0)
                .receivedAmount(1500.0)
                .pendingAmount(516.0)
                .paymentStatus(PaymentStatus.PARTIAL)
                .paymentMode(PaymentMode.UPI)
                .batchDate(today)
                .batchShift(Shift.AM)
                .notes("Daily retail dispatch sample")
                .build());

        upsertSale(SaleEntity.builder()
                .saleId("SALE_TST_COOP_01")
                .dispatchDate(today)
                .customerType(CustomerType.COOPERATIVE)
                .customerName("Jaipur Milk Cooperative")
                .productType(ProductType.MILK)
                .quantity(120.0)
                .unitPrice(38.0)
                .baseUnitPrice(35.5)
                .routeName("COOP-ROUTE-A")
                .collectionPoint("Central Chilling Plant")
                .fatPercent(4.5)
                .snfPercent(8.7)
                .fatRatePerKg(7.8)
                .snfRatePerKg(3.0)
                .qualityPricingApplied(true)
                .settlementCycle(SettlementCycle.WEEKLY)
                .reconciled(false)
                .reconciledAt(null)
                .reconciledBy(null)
                .reconciliationNote(null)
                .delivered(true)
                .deliveredAt(LocalDateTime.now().minusHours(4).atOffset(java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now())))
                .deliveredBy("delivery")
                .deliveryNote("Bulk tanker dispatch")
                .totalAmount(4560.0)
                .receivedAmount(4560.0)
                .pendingAmount(0.0)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMode(PaymentMode.BANK_TRANSFER)
                .batchDate(today)
                .batchShift(Shift.AM)
                .notes("Cooperative quality-linked settlement sample")
                .build());

        upsertSale(SaleEntity.builder()
                .saleId("SALE_TST_GHEE_01")
                .dispatchDate(today.minusDays(1))
                .customerType(CustomerType.INDIVIDUAL)
                .customerName("Anjali Home")
                .productType(ProductType.GHEE)
                .quantity(4.5)
                .unitPrice(620.0)
                .baseUnitPrice(620.0)
                .routeName("ROUTE-2")
                .collectionPoint("Vaishali Nagar")
                .qualityPricingApplied(false)
                .settlementCycle(SettlementCycle.MONTHLY)
                .reconciled(true)
                .reconciledAt(LocalDateTime.now().minusHours(1).atOffset(java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now())))
                .reconciledBy("manager")
                .reconciliationNote("Collected and marked settled")
                .delivered(true)
                .deliveredAt(LocalDateTime.now().minusDays(1).atOffset(java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now())))
                .deliveredBy("delivery")
                .deliveryNote("Packed jars delivered")
                .totalAmount(2790.0)
                .receivedAmount(2790.0)
                .pendingAmount(0.0)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMode(PaymentMode.CASH)
                .notes("Value-added product sample")
                .build());
        upsertSale(SaleEntity.builder()
                .saleId("SALE_TST_PENDING_01")
                .dispatchDate(today)
                .customerType(CustomerType.INDIVIDUAL)
                .customerName("Rohit Family")
                .productType(ProductType.MILK)
                .quantity(6.0)
                .unitPrice(64.0)
                .baseUnitPrice(64.0)
                .routeName("ROUTE-2")
                .collectionPoint("Vaishali Nagar")
                .qualityPricingApplied(false)
                .settlementCycle(SettlementCycle.DAILY)
                .reconciled(false)
                .reconciledAt(null)
                .reconciledBy(null)
                .reconciliationNote(null)
                .delivered(false)
                .deliveredAt(null)
                .deliveredBy(null)
                .deliveryNote("Delivery pending for evening route")
                .totalAmount(384.0)
                .receivedAmount(0.0)
                .pendingAmount(384.0)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMode(PaymentMode.CREDIT)
                .batchDate(today)
                .batchShift(Shift.PM)
                .notes("Pending delivery checklist sample")
                .build());
    }

    private void seedExpenses(LocalDate today) {
        upsertExpense(ExpenseEntity.builder()
                .expenseId("EXP_TST_SALARY_01")
                .expenseDate(today)
                .category(ExpenseCategory.SALARY)
                .amount(18500.0)
                .paymentMode(ExpensePaymentMode.BANK_TRANSFER)
                .referenceNo("SAL-MONTHLY-2026-02")
                .counterparty("Farm Staff")
                .notes("Monthly salary disbursement sample")
                .build());
        upsertExpense(ExpenseEntity.builder()
                .expenseId("EXP_TST_FEED_01")
                .expenseDate(today.minusDays(1))
                .category(ExpenseCategory.FEED)
                .amount(12480.0)
                .paymentMode(ExpensePaymentMode.UPI)
                .referenceNo("UPI-78223344")
                .counterparty("Rajasthan Feed Mills")
                .notes("Concentrate stock refill")
                .build());
        upsertExpense(ExpenseEntity.builder()
                .expenseId("EXP_TST_VET_01")
                .expenseDate(today.minusDays(1))
                .category(ExpenseCategory.VETERINARY)
                .amount(2650.0)
                .paymentMode(ExpensePaymentMode.CASH)
                .referenceNo("VET-OPD-334")
                .counterparty("Dr. Meena Vet Clinic")
                .notes("Mastitis and fever visit charges")
                .build());
        upsertExpense(ExpenseEntity.builder()
                .expenseId("EXP_TST_ELEC_01")
                .expenseDate(today.minusDays(2))
                .category(ExpenseCategory.ELECTRICITY)
                .amount(4180.0)
                .paymentMode(ExpensePaymentMode.BANK_TRANSFER)
                .referenceNo("JVVNL-99881")
                .counterparty("Jaipur Discom")
                .notes("Chilling plant electricity bill")
                .build());
    }

    private Map<String, String> resolveSeedAnimalIds() {
        Map<String, String> result = new HashMap<>();
        for (String tag : List.of(TAG_GIR, TAG_SAI, TAG_BUF, TAG_CALF, TAG_SICK)) {
            animalRepository.findByTag(tag).ifPresent(animal -> result.put(tag, animal.getAnimalId()));
        }
        return result;
    }

    private void upsertVaccination(VaccinationEntity payload) {
        vaccinationRepository.findById(payload.getVaccinationId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        vaccinationRepository.save(payload);
    }

    private void upsertDeworming(DewormingEntity payload) {
        dewormingRepository.findById(payload.getDewormingId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        dewormingRepository.save(payload);
    }

    private void upsertBreedingEvent(BreedingEventEntity payload) {
        breedingEventRepository.findById(payload.getBreedingEventId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        breedingEventRepository.save(payload);
    }

    private void upsertMedicalTreatment(MedicalTreatmentEntity payload) {
        medicalTreatmentRepository.findById(payload.getTreatmentId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        medicalTreatmentRepository.save(payload);
    }

    private void upsertFeedMaterial(FeedMaterialEntity payload) {
        feedMaterialRepository.findById(payload.getFeedMaterialId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        feedMaterialRepository.save(payload);
    }

    private void upsertFeedRecipe(FeedRecipeEntity payload) {
        feedRecipeRepository.findById(payload.getFeedRecipeId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        feedRecipeRepository.save(payload);
    }

    private void upsertFeedTask(FeedSopTaskEntity payload) {
        feedSopTaskRepository.findById(payload.getFeedTaskId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        feedSopTaskRepository.save(payload);
    }

    private void upsertFeedLog(FeedLogEntity payload) {
        feedLogRepository.findById(payload.getFeedLogId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        feedLogRepository.save(payload);
    }

    private void upsertCustomer(CustomerRecordEntity payload) {
        customerRecordRepository.findById(payload.getCustomerId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        customerRecordRepository.save(payload);
    }

    private void upsertSale(SaleEntity payload) {
        saleRepository.findById(payload.getSaleId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        saleRepository.save(payload);
    }

    private void upsertExpense(ExpenseEntity payload) {
        expenseRepository.findById(payload.getExpenseId())
                .ifPresent(existing -> payload.setCreatedAt(existing.getCreatedAt()));
        expenseRepository.save(payload);
    }

    private int migrateLegacyAnimalIds() {
        int migrated = 0;
        var legacyAnimals = animalRepository.findByAnimalIdStartingWith("AN_");
        for (AnimalEntity animal : legacyAnimals) {
            String oldId = animal.getAnimalId();
            String newId = buildAnimalId(animal.getBreed());
            replaceAnimalIdEverywhere(oldId, newId);
            migrated++;
        }
        return migrated;
    }

    private void replaceAnimalIdEverywhere(String oldId, String newId) {
        jdbcTemplate.update("update animal set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update animal set mother_animal_id = ? where mother_animal_id = ?", newId, oldId);
        jdbcTemplate.update("update milk_entry set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update feed_log set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update animal_vaccination set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update animal_deworming set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update breeding_event set animal_id = ? where animal_id = ?", newId, oldId);
        jdbcTemplate.update("update breeding_event set calf_animal_id = ? where calf_animal_id = ?", newId, oldId);
        jdbcTemplate.update("update animal_treatment set animal_id = ? where animal_id = ?", newId, oldId);
    }

    private String buildAnimalId(String breed) {
        String breedCode = buildBreedCode(breed);
        for (int i = 0; i < 20; i++) {
            String dateTimePart = LocalDateTime.now().format(ANIMAL_ID_TS);
            int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
            String candidate = breedCode + "_" + dateTimePart + "_" + suffix;
            if (!animalRepository.existsById(candidate)) {
                return candidate;
            }
        }
        return "ANM_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildBreedCode(String breed) {
        if (breed == null) {
            return "ANM";
        }
        String sanitized = breed.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (sanitized.isEmpty()) {
            return "ANM";
        }
        return sanitized.length() <= 3 ? sanitized : sanitized.substring(0, 3);
    }

    private String dayToken(LocalDate date) {
        return date.format(DAY_TOKEN);
    }

    private String compactToken(String value, int max) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "");
        if (sanitized.isEmpty()) {
            return "X";
        }
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }
}
