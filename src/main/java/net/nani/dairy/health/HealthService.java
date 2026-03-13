package net.nani.dairy.health;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.health.dto.*;
import net.nani.dairy.milk.MilkEntryEntity;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.milk.QcStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthService {

    private static final int PREGNANCY_CHECK_DUE_DAYS = 60;
    private static final int LOW_YIELD_DROP_PCT_THRESHOLD = 25;
    private static final Set<String> VACCINES_EVERY_6_MONTHS = Set.of("FMD");
    private static final Set<String> VACCINES_EVERY_12_MONTHS = Set.of("HS", "BQ", "ANTHRAX", "LSD");
    private static final Set<String> BRUCELLOSIS_KEYS = Set.of("BRUCELLOSIS");
    private static final Set<String> THEILERIOSIS_KEYS = Set.of("THEILERIOSIS");

    private final VaccinationRepository vaccinationRepository;
    private final DewormingRepository dewormingRepository;
    private final BreedingEventRepository breedingEventRepository;
    private final AnimalRepository animalRepository;
    private final MilkEntryRepository milkEntryRepository;

    public List<VaccinationResponse> listVaccinations(String animalId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        return vaccinationRepository.findByAnimalIdOrderByDoseDateDescCreatedAtDesc(normalizedAnimalId)
                .stream()
                .map(this::toVaccinationResponse)
                .toList();
    }

    public VaccinationResponse createVaccination(String animalId, CreateVaccinationRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        String vaccineName = trimRequired(req.getVaccineName(), "vaccineName");
        LocalDate resolvedNextDueDate = resolveNextDueDate(vaccineName, req.getDoseDate(), req.getNextDueDate());
        validateDates(req.getDoseDate(), resolvedNextDueDate, req.getBoosterDueDate(), req.getVaccineExpiryDate());

        VaccinationEntity entity = VaccinationEntity.builder()
                .vaccinationId(buildVaccinationId())
                .animalId(normalizedAnimalId)
                .vaccineName(vaccineName)
                .diseaseTarget(trimRequired(req.getDiseaseTarget(), "diseaseTarget"))
                .doseDate(req.getDoseDate())
                .doseNumber(req.getDoseNumber())
                .boosterDueDate(req.getBoosterDueDate())
                .nextDueDate(resolvedNextDueDate)
                .vaccineExpiryDate(req.getVaccineExpiryDate())
                .batchLotNo(trimToNull(req.getBatchLotNo()))
                .route(trimToNull(req.getRoute()))
                .notes(trimToNull(req.getNotes()))
                .build();

        return toVaccinationResponse(vaccinationRepository.save(entity));
    }

    public VaccinationResponse updateVaccination(String animalId, String vaccinationId, CreateVaccinationRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        String vaccineName = trimRequired(req.getVaccineName(), "vaccineName");
        LocalDate resolvedNextDueDate = resolveNextDueDate(vaccineName, req.getDoseDate(), req.getNextDueDate());
        validateDates(req.getDoseDate(), resolvedNextDueDate, req.getBoosterDueDate(), req.getVaccineExpiryDate());

        VaccinationEntity entity = vaccinationRepository.findByVaccinationIdAndAnimalId(vaccinationId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Vaccination record not found"));

        entity.setVaccineName(vaccineName);
        entity.setDiseaseTarget(trimRequired(req.getDiseaseTarget(), "diseaseTarget"));
        entity.setDoseDate(req.getDoseDate());
        entity.setDoseNumber(req.getDoseNumber());
        entity.setBoosterDueDate(req.getBoosterDueDate());
        entity.setNextDueDate(resolvedNextDueDate);
        entity.setVaccineExpiryDate(req.getVaccineExpiryDate());
        entity.setBatchLotNo(trimToNull(req.getBatchLotNo()));
        entity.setRoute(trimToNull(req.getRoute()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toVaccinationResponse(vaccinationRepository.save(entity));
    }

    public void deleteVaccination(String animalId, String vaccinationId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        VaccinationEntity entity = vaccinationRepository.findByVaccinationIdAndAnimalId(vaccinationId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Vaccination record not found"));
        vaccinationRepository.delete(entity);
    }

    public List<DewormingResponse> listDeworming(String animalId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        return dewormingRepository.findByAnimalIdOrderByDoseDateDescCreatedAtDesc(normalizedAnimalId)
                .stream()
                .map(this::toDewormingResponse)
                .toList();
    }

    public DewormingResponse createDeworming(String animalId, CreateDewormingRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateDates(req.getDoseDate(), req.getNextDueDate(), null, null);
        validateWeight(req.getWeightAtDoseKg());

        DewormingEntity entity = DewormingEntity.builder()
                .dewormingId(buildDewormingId())
                .animalId(normalizedAnimalId)
                .drugName(trimRequired(req.getDrugName(), "drugName"))
                .doseDate(req.getDoseDate())
                .nextDueDate(req.getNextDueDate())
                .weightAtDoseKg(req.getWeightAtDoseKg())
                .notes(trimToNull(req.getNotes()))
                .build();

        return toDewormingResponse(dewormingRepository.save(entity));
    }

    public DewormingResponse updateDeworming(String animalId, String dewormingId, CreateDewormingRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateDates(req.getDoseDate(), req.getNextDueDate(), null, null);
        validateWeight(req.getWeightAtDoseKg());

        DewormingEntity entity = dewormingRepository.findByDewormingIdAndAnimalId(dewormingId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Deworming record not found"));

        entity.setDrugName(trimRequired(req.getDrugName(), "drugName"));
        entity.setDoseDate(req.getDoseDate());
        entity.setNextDueDate(req.getNextDueDate());
        entity.setWeightAtDoseKg(req.getWeightAtDoseKg());
        entity.setNotes(trimToNull(req.getNotes()));

        return toDewormingResponse(dewormingRepository.save(entity));
    }

    public void deleteDeworming(String animalId, String dewormingId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        DewormingEntity entity = dewormingRepository.findByDewormingIdAndAnimalId(dewormingId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Deworming record not found"));
        dewormingRepository.delete(entity);
    }

    public HealthSummaryResponse summary(LocalDate date, Integer windowDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeWindowDays = sanitizeWindowDays(windowDays);
        LocalDate toDate = effectiveDate.plusDays(safeWindowDays);
        List<VaccinationEntity> vaccinationRows = vaccinationRepository.findAll();

        long vaccinationsDueToday = vaccinationRows.stream()
                .map(this::resolveVaccinationDueDate)
                .filter(Objects::nonNull)
                .filter(dueDate -> dueDate.isEqual(effectiveDate))
                .count();
        long vaccinationsDueSoon = vaccinationRows.stream()
                .map(this::resolveVaccinationDueDate)
                .filter(Objects::nonNull)
                .filter(dueDate -> dueDate.isAfter(effectiveDate) && !dueDate.isAfter(toDate))
                .count();
        long vaccinationsOverdue = vaccinationRows.stream()
                .map(this::resolveVaccinationDueDate)
                .filter(Objects::nonNull)
                .filter(dueDate -> dueDate.isBefore(effectiveDate))
                .count();

        return HealthSummaryResponse.builder()
                .date(effectiveDate)
                .windowDays(safeWindowDays)
                .vaccinationsDueToday(vaccinationsDueToday)
                .vaccinationsDueSoon(vaccinationsDueSoon)
                .vaccinationsOverdue(vaccinationsOverdue)
                .dewormingDueToday(dewormingRepository.countByNextDueDate(effectiveDate))
                .dewormingDueSoon(dewormingRepository.countByNextDueDateAfterAndNextDueDateLessThanEqual(effectiveDate, toDate))
                .dewormingOverdue(dewormingRepository.countByNextDueDateBefore(effectiveDate))
                .build();
    }

    public HealthProtocolResponse healthProtocol(String animalId, LocalDate date, Integer windowDays) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        AnimalEntity animal = animalRepository.findById(normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found for animalId"));

        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeWindowDays = sanitizeWindowDays(windowDays);
        LocalDate toDate = effectiveDate.plusDays(safeWindowDays);
        LocalDate fromDate = effectiveDate.minusDays(30);

        List<VaccinationEntity> vaccinationRows = vaccinationRepository.findByAnimalIdOrderByDoseDateDescCreatedAtDesc(normalizedAnimalId);
        List<DewormingEntity> dewormingRows = dewormingRepository.findByAnimalIdOrderByDoseDateDescCreatedAtDesc(normalizedAnimalId);
        List<BreedingEventEntity> breedingRows = breedingEventRepository.findByAnimalIdOrderByHeatDateDescCreatedAtDesc(normalizedAnimalId);
        List<MilkEntryEntity> milkRows = milkEntryRepository.findByAnimalIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                normalizedAnimalId,
                fromDate,
                effectiveDate
        );

        List<HealthProtocolItemResponse> items = new ArrayList<>();
        addRoutineProtocolItems(animal, breedingRows, effectiveDate, items);
        addVaccinationProtocolItems(vaccinationRows, effectiveDate, toDate, items);
        addDewormingProtocolItems(dewormingRows, effectiveDate, toDate, items);
        addBreedingProtocolItems(breedingRows, effectiveDate, toDate, items);
        addMastitisProtocolItems(milkRows, effectiveDate, items);
        addLowYieldProtocolItems(animal, milkRows, effectiveDate, items);

        items.sort(healthProtocolComparator());

        Long highPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.HIGH).count();
        Long mediumPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.MEDIUM).count();
        Long lowPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.LOW).count();
        Long ageDays = ageDays(animal.getDateOfBirth(), effectiveDate);
        Integer ageMonths = ageMonths(animal.getDateOfBirth(), effectiveDate);

        return HealthProtocolResponse.builder()
                .date(effectiveDate)
                .windowDays(safeWindowDays)
                .animalId(animal.getAnimalId())
                .animalTag(animal.getTag())
                .animalStatus(animal.getStatus())
                .growthStage(animal.getGrowthStage())
                .ageDays(ageDays)
                .ageMonths(ageMonths)
                .totalItems(items.size())
                .highPriorityCount(highPriorityCount)
                .mediumPriorityCount(mediumPriorityCount)
                .lowPriorityCount(lowPriorityCount)
                .items(items)
                .build();
    }

    public List<BreedingEventResponse> listBreedingEvents(String animalId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        return breedingEventRepository.findByAnimalIdOrderByHeatDateDescCreatedAtDesc(normalizedAnimalId)
                .stream()
                .map(this::toBreedingEventResponse)
                .toList();
    }

    public BreedingEventResponse createBreedingEvent(String animalId, CreateBreedingEventRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateBreedingDates(req);

        BreedingEventEntity entity = BreedingEventEntity.builder()
                .breedingEventId(buildBreedingEventId())
                .animalId(normalizedAnimalId)
                .heatDate(req.getHeatDate())
                .inseminationDate(req.getInseminationDate())
                .sireTag(trimToNull(req.getSireTag()))
                .pregnancyCheckDate(req.getPregnancyCheckDate())
                .pregnancyResult(defaultPregnancyResult(req.getPregnancyResult()))
                .expectedCalvingDate(req.getExpectedCalvingDate())
                .actualCalvingDate(req.getActualCalvingDate())
                .calfAnimalId(trimToNull(req.getCalfAnimalId()))
                .calfTag(trimToNull(req.getCalfTag()))
                .calfGender(defaultCalfGender(req.getCalfGender()))
                .calvingOutcome(defaultCalvingOutcome(req.getCalvingOutcome()))
                .notes(trimToNull(req.getNotes()))
                .build();

        return toBreedingEventResponse(breedingEventRepository.save(entity));
    }

    public BreedingEventResponse updateBreedingEvent(String animalId, String breedingEventId, CreateBreedingEventRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateBreedingDates(req);

        BreedingEventEntity entity = breedingEventRepository
                .findByBreedingEventIdAndAnimalId(breedingEventId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Breeding event not found"));

        entity.setHeatDate(req.getHeatDate());
        entity.setInseminationDate(req.getInseminationDate());
        entity.setSireTag(trimToNull(req.getSireTag()));
        entity.setPregnancyCheckDate(req.getPregnancyCheckDate());
        entity.setPregnancyResult(defaultPregnancyResult(req.getPregnancyResult()));
        entity.setExpectedCalvingDate(req.getExpectedCalvingDate());
        entity.setActualCalvingDate(req.getActualCalvingDate());
        entity.setCalfAnimalId(trimToNull(req.getCalfAnimalId()));
        entity.setCalfTag(trimToNull(req.getCalfTag()));
        entity.setCalfGender(defaultCalfGender(req.getCalfGender()));
        entity.setCalvingOutcome(defaultCalvingOutcome(req.getCalvingOutcome()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toBreedingEventResponse(breedingEventRepository.save(entity));
    }

    public void deleteBreedingEvent(String animalId, String breedingEventId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        BreedingEventEntity entity = breedingEventRepository
                .findByBreedingEventIdAndAnimalId(breedingEventId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Breeding event not found"));
        breedingEventRepository.delete(entity);
    }

    public BreedingSummaryResponse breedingSummary(LocalDate date, Integer windowDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeWindowDays = sanitizeWindowDays(windowDays);
        LocalDate toDate = effectiveDate.plusDays(safeWindowDays);

        return BreedingSummaryResponse.builder()
                .date(effectiveDate)
                .windowDays(safeWindowDays)
                .calvingDueToday(breedingEventRepository.countByExpectedCalvingDateAndActualCalvingDateIsNull(effectiveDate))
                .calvingDueSoon(
                        breedingEventRepository
                                .countByExpectedCalvingDateAfterAndExpectedCalvingDateLessThanEqualAndActualCalvingDateIsNull(
                                        effectiveDate,
                                        toDate
                                )
                )
                .calvingOverdue(breedingEventRepository.countByExpectedCalvingDateBeforeAndActualCalvingDateIsNull(effectiveDate))
                .openPregnancies(
                        breedingEventRepository.countByPregnancyResultAndActualCalvingDateIsNull(
                                BreedingPregnancyResult.PREGNANT
                        )
                )
                .build();
    }

    private void addRoutineProtocolItems(
            AnimalEntity animal,
            List<BreedingEventEntity> breedingRows,
            LocalDate effectiveDate,
            List<HealthProtocolItemResponse> items
    ) {
        addProtocolItem(
                items,
                "OBS_FEED_WATER",
                "OBSERVATION",
                "Feed and water intake check",
                "Confirm appetite and water intake are normal compared to this animal baseline.",
                WorklistPriority.MEDIUM,
                WorklistDueStatus.DUE_TODAY,
                effectiveDate,
                animal.getAnimalId()
        );
        addProtocolItem(
                items,
                "OBS_ACTIVITY",
                "OBSERVATION",
                "Rumination and activity check",
                "Observe rumination, alertness, gait and general behavior change.",
                WorklistPriority.MEDIUM,
                WorklistDueStatus.DUE_TODAY,
                effectiveDate,
                animal.getAnimalId()
        );
        addProtocolItem(
                items,
                "OBS_DUNG_URINE",
                "OBSERVATION",
                "Dung and urine consistency check",
                "Check stool and urine consistency/frequency for early digestive or urinary issues.",
                WorklistPriority.MEDIUM,
                WorklistDueStatus.DUE_TODAY,
                effectiveDate,
                animal.getAnimalId()
        );

        if (animal.getStatus() == AnimalStatus.LACTATING) {
            addProtocolItem(
                    items,
                    "LAC_UDDER",
                    "MILK_HEALTH",
                    "Udder and teat exam",
                    "Check udder heat/swelling, teat injury and pain before milking.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "LAC_MILK_VISUAL",
                    "MILK_HEALTH",
                    "Strip-cup milk quality check",
                    "Verify first streams for clots, flakes, blood, smell or color changes.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "LAC_TEAT_DIP",
                    "HYGIENE",
                    "Post-milking teat dip compliance",
                    "Ensure teat dip is completed to reduce mastitis risk.",
                    WorklistPriority.MEDIUM,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
        } else if (animal.getStatus() == AnimalStatus.DRY) {
            addProtocolItem(
                    items,
                    "DRY_BCS",
                    "NUTRITION",
                    "Dry-period body condition review",
                    "Track body condition and adjust ration to prevent over/under-conditioning.",
                    WorklistPriority.MEDIUM,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "DRY_UDDER",
                    "MILK_HEALTH",
                    "Dry udder health watch",
                    "Observe udder for edema, leakage or infection signs during dry period.",
                    WorklistPriority.MEDIUM,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
        } else if (animal.getStatus() == AnimalStatus.SICK) {
            addProtocolItem(
                    items,
                    "SICK_TEMP",
                    "CLINICAL",
                    "Temperature monitoring (2x/day)",
                    "Record morning and evening temperature and compare with treatment plan.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "SICK_MED",
                    "CLINICAL",
                    "Medication compliance check",
                    "Confirm dose, route and timing adherence for active treatments.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "SICK_ISOLATION",
                    "BIOSECURITY",
                    "Isolation and pen hygiene check",
                    "Verify isolation, bedding hygiene and cross-contamination prevention.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
        }

        Long ageDays = ageDays(animal.getDateOfBirth(), effectiveDate);
        boolean calfOrGrower = animal.getGrowthStage() == net.nani.dairy.animals.AnimalGrowthStage.CALF
                || animal.getGrowthStage() == net.nani.dairy.animals.AnimalGrowthStage.GROWER
                || (ageDays != null && ageDays <= 365);
        if (calfOrGrower) {
            addProtocolItem(
                    items,
                    "CALF_GI_RESP",
                    "CALF_CARE",
                    "Calf respiratory/GI symptom check",
                    "Check nasal discharge, cough, diarrhea and dehydration signs.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "CALF_FEED_PROGRESS",
                    "CALF_CARE",
                    "Calf feeding progress check",
                    "Confirm milk/starter feed intake and transition progress by age.",
                    WorklistPriority.MEDIUM,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animal.getAnimalId()
            );
            addProtocolItem(
                    items,
                    "CALF_WEIGHT_WEEKLY",
                    "CALF_CARE",
                    "Weekly weight trend check",
                    "Track growth trend and review if weight gain is below expected curve.",
                    WorklistPriority.MEDIUM,
                    WorklistDueStatus.DUE_SOON,
                    effectiveDate.plusDays(7),
                    animal.getAnimalId()
            );
        }

        BreedingEventEntity openPregnancy = breedingRows.stream()
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.PREGNANT)
                .filter(row -> row.getActualCalvingDate() == null)
                .filter(row -> row.getExpectedCalvingDate() != null)
                .min(Comparator.comparing(BreedingEventEntity::getExpectedCalvingDate))
                .orElse(null);
        if (openPregnancy != null) {
            WorklistDueStatus dueStatus = dueStatus(openPregnancy.getExpectedCalvingDate(), effectiveDate);
            WorklistPriority priority = dueStatus == WorklistDueStatus.OVERDUE ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
            addProtocolItem(
                    items,
                    "PREG_CALVING_WATCH",
                    "BREEDING",
                    "Pregnancy and calving readiness check",
                    "Review pre-calving signs, calfing kit readiness and assistance plan.",
                    priority,
                    dueStatus,
                    openPregnancy.getExpectedCalvingDate(),
                    animal.getAnimalId()
            );
        }
    }

    private void addVaccinationProtocolItems(
            List<VaccinationEntity> vaccinationRows,
            LocalDate effectiveDate,
            LocalDate toDate,
            List<HealthProtocolItemResponse> items
    ) {
        Map<String, VaccinationDueCandidate> dueByVaccine = new LinkedHashMap<>();
        for (VaccinationEntity row : vaccinationRows) {
            LocalDate dueDate = resolveVaccinationDueDate(row);
            if (dueDate == null || dueDate.isAfter(toDate)) {
                continue;
            }
            String key = normalizeVaccineKey(row.getVaccineName());
            VaccinationDueCandidate existing = dueByVaccine.get(key);
            if (existing == null || dueDate.isBefore(existing.dueDate)) {
                dueByVaccine.put(key, new VaccinationDueCandidate(row, dueDate));
            }
        }

        dueByVaccine.values().stream()
                .sorted(Comparator.comparing(row -> row.dueDate))
                .forEach(row -> {
                    WorklistDueStatus dueStatus = dueStatus(row.dueDate, effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.DUE_SOON ? WorklistPriority.MEDIUM : WorklistPriority.HIGH;
                    addProtocolItem(
                            items,
                            "VACCINE_DUE",
                            "PREVENTIVE",
                            "Vaccination due",
                            "Vaccine " + row.entity.getVaccineName() + " for " + row.entity.getDiseaseTarget() + " is due.",
                            priority,
                            dueStatus,
                            row.dueDate,
                            row.entity.getVaccinationId()
                    );
                });
    }

    private void addDewormingProtocolItems(
            List<DewormingEntity> dewormingRows,
            LocalDate effectiveDate,
            LocalDate toDate,
            List<HealthProtocolItemResponse> items
    ) {
        Map<String, DewormingEntity> dueByDrug = new LinkedHashMap<>();
        for (DewormingEntity row : dewormingRows) {
            if (row.getNextDueDate() == null || row.getNextDueDate().isAfter(toDate)) {
                continue;
            }
            String key = row.getDrugName() == null ? "" : row.getDrugName().trim().toUpperCase(Locale.ROOT);
            DewormingEntity existing = dueByDrug.get(key);
            if (existing == null || row.getNextDueDate().isBefore(existing.getNextDueDate())) {
                dueByDrug.put(key, row);
            }
        }

        dueByDrug.values().stream()
                .sorted(Comparator.comparing(DewormingEntity::getNextDueDate))
                .forEach(row -> {
                    WorklistDueStatus dueStatus = dueStatus(row.getNextDueDate(), effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.DUE_SOON ? WorklistPriority.MEDIUM : WorklistPriority.HIGH;
                    addProtocolItem(
                            items,
                            "DEWORM_DUE",
                            "PREVENTIVE",
                            "Deworming due",
                            "Deworming follow-up for drug " + row.getDrugName() + " is due.",
                            priority,
                            dueStatus,
                            row.getNextDueDate(),
                            row.getDewormingId()
                    );
                });
    }

    private void addBreedingProtocolItems(
            List<BreedingEventEntity> breedingRows,
            LocalDate effectiveDate,
            LocalDate toDate,
            List<HealthProtocolItemResponse> items
    ) {
        BreedingProtocolCandidate pregnancyCheck = breedingRows.stream()
                .filter(row -> row.getInseminationDate() != null)
                .filter(row -> row.getPregnancyCheckDate() == null)
                .filter(row -> row.getActualCalvingDate() == null)
                .map(row -> new BreedingProtocolCandidate(row, row.getInseminationDate().plusDays(PREGNANCY_CHECK_DUE_DAYS)))
                .filter(row -> !row.dueDate.isAfter(toDate))
                .min(Comparator.comparing(row -> row.dueDate))
                .orElse(null);
        if (pregnancyCheck != null) {
            WorklistDueStatus dueStatus = dueStatus(pregnancyCheck.dueDate, effectiveDate);
            WorklistPriority priority = dueStatus == WorklistDueStatus.OVERDUE ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
            addProtocolItem(
                    items,
                    "PREG_CHECK_DUE",
                    "BREEDING",
                    "Pregnancy check due",
                    "AI on " + pregnancyCheck.entity.getInseminationDate() + " needs pregnancy confirmation.",
                    priority,
                    dueStatus,
                    pregnancyCheck.dueDate,
                    pregnancyCheck.entity.getBreedingEventId()
            );
        }

        BreedingEventEntity calvingWatch = breedingRows.stream()
                .filter(row -> row.getExpectedCalvingDate() != null)
                .filter(row -> row.getActualCalvingDate() == null)
                .filter(row -> !row.getExpectedCalvingDate().isAfter(toDate))
                .min(Comparator.comparing(BreedingEventEntity::getExpectedCalvingDate))
                .orElse(null);
        if (calvingWatch != null) {
            WorklistDueStatus dueStatus = dueStatus(calvingWatch.getExpectedCalvingDate(), effectiveDate);
            WorklistPriority priority = dueStatus == WorklistDueStatus.OVERDUE ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
            addProtocolItem(
                    items,
                    "CALVING_DUE",
                    "BREEDING",
                    "Calving watch",
                    "Expected calving date is " + calvingWatch.getExpectedCalvingDate() + ".",
                    priority,
                    dueStatus,
                    calvingWatch.getExpectedCalvingDate(),
                    calvingWatch.getBreedingEventId()
            );
        }

        LocalDate fromDate = effectiveDate.minusDays(365);
        long failedAttempts = breedingRows.stream()
                .filter(row -> row.getInseminationDate() != null)
                .filter(row -> row.getInseminationDate().isAfter(fromDate.minusDays(1)))
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                .count();
        if (failedAttempts >= 3) {
            addProtocolItem(
                    items,
                    "REPEAT_BREEDER",
                    "BREEDING",
                    "Repeat breeder risk",
                    failedAttempts + " unsuccessful breeding attempts in the last 365 days.",
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    "RBR"
            );
        }
    }

    private void addMastitisProtocolItems(
            List<MilkEntryEntity> milkRows,
            LocalDate effectiveDate,
            List<HealthProtocolItemResponse> items
    ) {
        LocalDate fromDate = effectiveDate.minusDays(14);
        MilkEntryEntity latestFlagged = milkRows.stream()
                .filter(row -> !row.getDate().isBefore(fromDate))
                .filter(row -> row.getQcStatus() == QcStatus.HOLD || row.getQcStatus() == QcStatus.REJECT)
                .filter(row -> containsMastitisSignal(row.getRejectionReason(), row.getSmellNotes()))
                .max(Comparator.comparing(MilkEntryEntity::getDate))
                .orElse(null);
        if (latestFlagged == null) {
            return;
        }

        LocalDate followUpDate = latestFlagged.getDate().plusDays(1);
        addProtocolItem(
                items,
                "MASTITIS_FOLLOW_UP",
                "CLINICAL",
                "Mastitis follow-up check",
                "QC flagged mastitis signal on " + latestFlagged.getDate() + ". Re-check udder and milk quality.",
                WorklistPriority.HIGH,
                dueStatus(followUpDate, effectiveDate),
                followUpDate,
                latestFlagged.getMilkEntryId()
        );
    }

    private void addLowYieldProtocolItems(
            AnimalEntity animal,
            List<MilkEntryEntity> milkRows,
            LocalDate effectiveDate,
            List<HealthProtocolItemResponse> items
    ) {
        if (animal.getStatus() != AnimalStatus.LACTATING) {
            return;
        }
        if (milkRows.isEmpty()) {
            return;
        }

        Map<LocalDate, Double> dailyLiters = new HashMap<>();
        for (MilkEntryEntity row : milkRows) {
            dailyLiters.merge(row.getDate(), row.getLiters(), Double::sum);
        }

        AvgWindow baseline = averageBetween(dailyLiters, effectiveDate.minusDays(9), effectiveDate.minusDays(3));
        AvgWindow recent = averageBetween(dailyLiters, effectiveDate.minusDays(2), effectiveDate);
        if (baseline.days < 3 || recent.days < 2 || baseline.avg <= 0.1d) {
            return;
        }

        double dropPct = ((baseline.avg - recent.avg) / baseline.avg) * 100d;
        if (dropPct < LOW_YIELD_DROP_PCT_THRESHOLD) {
            return;
        }

        WorklistPriority priority = dropPct >= 40d ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
        addProtocolItem(
                items,
                "LOW_YIELD_ALERT",
                "PRODUCTION",
                "Low yield anomaly follow-up",
                "Recent " + formatNumber(recent.avg) + " L/day vs baseline " + formatNumber(baseline.avg)
                        + " L/day (drop " + Math.round(dropPct) + "%). Review feed, health and heat stress.",
                priority,
                WorklistDueStatus.DUE_TODAY,
                effectiveDate,
                "LYD"
        );
    }

    private Comparator<HealthProtocolItemResponse> healthProtocolComparator() {
        return Comparator
                .comparingInt((HealthProtocolItemResponse item) -> priorityRank(item.getPriority()))
                .thenComparingInt(item -> dueStatusRank(item.getDueStatus()))
                .thenComparing(HealthProtocolItemResponse::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> item.getCode() == null ? "" : item.getCode())
                .thenComparing(item -> item.getProtocolId() == null ? "" : item.getProtocolId());
    }

    private void addProtocolItem(
            List<HealthProtocolItemResponse> items,
            String code,
            String category,
            String title,
            String description,
            WorklistPriority priority,
            WorklistDueStatus dueStatus,
            LocalDate dueDate,
            String sourceId
    ) {
        items.add(HealthProtocolItemResponse.builder()
                .protocolId(buildHealthProtocolId(code, sourceId))
                .code(code)
                .category(category)
                .title(title)
                .description(description)
                .priority(priority)
                .dueStatus(dueStatus)
                .dueDate(dueDate)
                .build());
    }

    private String buildHealthProtocolId(String code, String sourceId) {
        String safeCode = (code == null ? "ITEM" : code.trim()).replaceAll("[^A-Za-z0-9_-]", "_");
        String safeSource = (sourceId == null ? "" : sourceId.trim()).replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeSource.isEmpty()) {
            safeSource = UUID.randomUUID().toString().substring(0, 8);
        }
        return "HPT_" + safeCode + "_" + safeSource;
    }

    private Long ageDays(LocalDate dateOfBirth, LocalDate effectiveDate) {
        if (dateOfBirth == null) {
            return null;
        }
        if (dateOfBirth.isAfter(effectiveDate)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dateOfBirth, effectiveDate);
    }

    private Integer ageMonths(LocalDate dateOfBirth, LocalDate effectiveDate) {
        Long days = ageDays(dateOfBirth, effectiveDate);
        if (days == null) {
            return null;
        }
        return (int) (days / 30L);
    }

    public WorklistResponse todayWorklist(LocalDate date, Integer windowDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeWindowDays = sanitizeWindowDays(windowDays);
        LocalDate toDate = effectiveDate.plusDays(safeWindowDays);

        List<WorklistItemResponse> items = new ArrayList<>();
        addVaccinationTasks(effectiveDate, toDate, items);
        addDewormingTasks(effectiveDate, toDate, items);
        addPregnancyCheckTasks(effectiveDate, toDate, items);
        addCalvingTasks(effectiveDate, toDate, items);
        addRepeatBreederTasks(effectiveDate, items);
        addMastitisFollowUpTasks(effectiveDate, items);
        addLowYieldTasks(effectiveDate, items);

        hydrateAnimalTags(items);
        items.sort(worklistComparator());

        long highPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.HIGH).count();
        long mediumPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.MEDIUM).count();
        long lowPriorityCount = items.stream().filter(i -> i.getPriority() == WorklistPriority.LOW).count();
        long overdueCount = items.stream().filter(i -> i.getDueStatus() == WorklistDueStatus.OVERDUE).count();
        long dueTodayCount = items.stream().filter(i -> i.getDueStatus() == WorklistDueStatus.DUE_TODAY).count();
        long dueSoonCount = items.stream().filter(i -> i.getDueStatus() == WorklistDueStatus.DUE_SOON).count();

        return WorklistResponse.builder()
                .date(effectiveDate)
                .windowDays(safeWindowDays)
                .generatedAt(OffsetDateTime.now())
                .totalTasks(items.size())
                .highPriorityCount(highPriorityCount)
                .mediumPriorityCount(mediumPriorityCount)
                .lowPriorityCount(lowPriorityCount)
                .overdueCount(overdueCount)
                .dueTodayCount(dueTodayCount)
                .dueSoonCount(dueSoonCount)
                .items(items)
                .build();
    }

    private void addVaccinationTasks(LocalDate effectiveDate, LocalDate toDate, List<WorklistItemResponse> items) {
        vaccinationRepository.findAll().stream()
                .map(row -> new VaccinationDueCandidate(row, resolveVaccinationDueDate(row)))
                .filter(row -> row.dueDate != null && !row.dueDate.isAfter(toDate))
                .sorted(Comparator.comparing(row -> row.dueDate))
                .forEach(row -> {
                    WorklistDueStatus dueStatus = dueStatus(row.dueDate, effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.DUE_SOON ? WorklistPriority.MEDIUM : WorklistPriority.HIGH;
                    items.add(buildWorklistItem(
                            "VAC",
                            row.entity.getVaccinationId(),
                            WorklistTaskType.VACCINATION_DUE,
                            priority,
                            dueStatus,
                            row.dueDate,
                            row.entity.getAnimalId(),
                            "Vaccination due",
                            "Vaccine " + row.entity.getVaccineName() + " for " + row.entity.getDiseaseTarget()
                    ));
                });
    }

    private void addDewormingTasks(LocalDate effectiveDate, LocalDate toDate, List<WorklistItemResponse> items) {
        dewormingRepository.findByNextDueDateIsNotNullAndNextDueDateLessThanEqualOrderByNextDueDateAsc(toDate)
                .forEach(row -> {
                    WorklistDueStatus dueStatus = dueStatus(row.getNextDueDate(), effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.DUE_SOON ? WorklistPriority.MEDIUM : WorklistPriority.HIGH;
                    items.add(buildWorklistItem(
                            "DWM",
                            row.getDewormingId(),
                            WorklistTaskType.DEWORMING_DUE,
                            priority,
                            dueStatus,
                            row.getNextDueDate(),
                            row.getAnimalId(),
                            "Deworming due",
                            "Drug " + row.getDrugName() + " follow-up is due"
                    ));
                });
    }

    private void addPregnancyCheckTasks(LocalDate effectiveDate, LocalDate toDate, List<WorklistItemResponse> items) {
        breedingEventRepository.findByInseminationDateIsNotNullAndPregnancyCheckDateIsNullAndActualCalvingDateIsNullOrderByInseminationDateAsc()
                .forEach(row -> {
                    LocalDate dueDate = row.getInseminationDate().plusDays(PREGNANCY_CHECK_DUE_DAYS);
                    if (dueDate.isAfter(toDate)) {
                        return;
                    }

                    WorklistDueStatus dueStatus = dueStatus(dueDate, effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.OVERDUE ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
                    items.add(buildWorklistItem(
                            "PRG",
                            row.getBreedingEventId(),
                            WorklistTaskType.PREGNANCY_CHECK_DUE,
                            priority,
                            dueStatus,
                            dueDate,
                            row.getAnimalId(),
                            "Pregnancy check due",
                            "AI on " + row.getInseminationDate() + " and pregnancy check is pending"
                    ));
                });
    }

    private void addCalvingTasks(LocalDate effectiveDate, LocalDate toDate, List<WorklistItemResponse> items) {
        breedingEventRepository
                .findByExpectedCalvingDateIsNotNullAndExpectedCalvingDateLessThanEqualAndActualCalvingDateIsNullOrderByExpectedCalvingDateAsc(toDate)
                .forEach(row -> {
                    WorklistDueStatus dueStatus = dueStatus(row.getExpectedCalvingDate(), effectiveDate);
                    WorklistPriority priority = dueStatus == WorklistDueStatus.OVERDUE ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
                    items.add(buildWorklistItem(
                            "CLV",
                            row.getBreedingEventId(),
                            WorklistTaskType.CALVING_DUE,
                            priority,
                            dueStatus,
                            row.getExpectedCalvingDate(),
                            row.getAnimalId(),
                            "Calving watch",
                            "Expected calving date is " + row.getExpectedCalvingDate()
                    ));
                });
    }

    private void addRepeatBreederTasks(LocalDate effectiveDate, List<WorklistItemResponse> items) {
        LocalDate fromDate = effectiveDate.minusDays(365);
        List<BreedingEventEntity> rows = breedingEventRepository
                .findByPregnancyResultAndInseminationDateIsNotNullAndActualCalvingDateIsNullAndInseminationDateAfter(
                        BreedingPregnancyResult.NOT_PREGNANT,
                        fromDate.minusDays(1)
                );

        Map<String, Long> attemptsByAnimal = rows.stream()
                .collect(Collectors.groupingBy(BreedingEventEntity::getAnimalId, Collectors.counting()));

        Map<String, LocalDate> lastAiDateByAnimal = rows.stream().collect(
                Collectors.groupingBy(
                        BreedingEventEntity::getAnimalId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(BreedingEventEntity::getInseminationDate)),
                                maxRow -> maxRow.map(BreedingEventEntity::getInseminationDate).orElse(null)
                        )
                )
        );

        attemptsByAnimal.forEach((animalId, failedAttempts) -> {
            if (failedAttempts < 3) {
                return;
            }
            LocalDate lastAiDate = lastAiDateByAnimal.get(animalId);
            items.add(buildWorklistItem(
                    "RBR",
                    animalId,
                    WorklistTaskType.REPEAT_BREEDER,
                    WorklistPriority.HIGH,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animalId,
                    "Repeat breeder risk",
                    failedAttempts + " unsuccessful breeding attempts in last 365 days"
                            + (lastAiDate != null ? " (last AI: " + lastAiDate + ")" : "")
            ));
        });
    }

    private void addMastitisFollowUpTasks(LocalDate effectiveDate, List<WorklistItemResponse> items) {
        LocalDate fromDate = effectiveDate.minusDays(14);
        List<MilkEntryEntity> rows = milkEntryRepository.findByDateBetweenAndQcStatusIn(
                fromDate,
                effectiveDate,
                EnumSet.of(QcStatus.HOLD, QcStatus.REJECT)
        );

        Map<String, MilkEntryEntity> latestByAnimal = new HashMap<>();
        for (MilkEntryEntity row : rows) {
            if (!containsMastitisSignal(row.getRejectionReason(), row.getSmellNotes())) {
                continue;
            }
            MilkEntryEntity current = latestByAnimal.get(row.getAnimalId());
            if (current == null || row.getDate().isAfter(current.getDate())) {
                latestByAnimal.put(row.getAnimalId(), row);
            }
        }

        latestByAnimal.forEach((animalId, row) -> {
            LocalDate dueDate = row.getDate().plusDays(1);
            items.add(buildWorklistItem(
                    "MST",
                    row.getMilkEntryId(),
                    WorklistTaskType.MASTITIS_FOLLOW_UP,
                    WorklistPriority.HIGH,
                    dueStatus(dueDate, effectiveDate),
                    dueDate,
                    animalId,
                    "Mastitis follow-up",
                    "QC flagged mastitis risk on " + row.getDate() + " (" + row.getQcStatus() + ")"
            ));
        });
    }

    private void addLowYieldTasks(LocalDate effectiveDate, List<WorklistItemResponse> items) {
        List<AnimalEntity> activeLactating = animalRepository.findByIsActiveAndStatus(true, AnimalStatus.LACTATING);
        if (activeLactating.isEmpty()) {
            return;
        }

        List<String> animalIds = activeLactating.stream().map(AnimalEntity::getAnimalId).toList();
        LocalDate fromDate = effectiveDate.minusDays(9);
        List<MilkEntryEntity> rows = milkEntryRepository.findByAnimalIdInAndDateBetween(animalIds, fromDate, effectiveDate);
        if (rows.isEmpty()) {
            return;
        }

        Map<String, Map<LocalDate, Double>> dailyLitersByAnimal = new HashMap<>();
        for (MilkEntryEntity row : rows) {
            dailyLitersByAnimal
                    .computeIfAbsent(row.getAnimalId(), ignored -> new HashMap<>())
                    .merge(row.getDate(), row.getLiters(), Double::sum);
        }

        for (String animalId : animalIds) {
            Map<LocalDate, Double> dailyLiters = dailyLitersByAnimal.get(animalId);
            if (dailyLiters == null || dailyLiters.isEmpty()) {
                continue;
            }

            AvgWindow baseline = averageBetween(dailyLiters, effectiveDate.minusDays(9), effectiveDate.minusDays(3));
            AvgWindow recent = averageBetween(dailyLiters, effectiveDate.minusDays(2), effectiveDate);
            if (baseline.days < 3 || recent.days < 2 || baseline.avg <= 0.1d) {
                continue;
            }

            double dropPct = ((baseline.avg - recent.avg) / baseline.avg) * 100d;
            if (dropPct < LOW_YIELD_DROP_PCT_THRESHOLD) {
                continue;
            }

            WorklistPriority priority = dropPct >= 40d ? WorklistPriority.HIGH : WorklistPriority.MEDIUM;
            items.add(buildWorklistItem(
                    "LYD",
                    animalId + "_" + effectiveDate,
                    WorklistTaskType.LOW_YIELD,
                    priority,
                    WorklistDueStatus.DUE_TODAY,
                    effectiveDate,
                    animalId,
                    "Low milk yield alert",
                    "Recent " + formatNumber(recent.avg) + " L/day vs baseline " + formatNumber(baseline.avg)
                            + " L/day (drop " + Math.round(dropPct) + "%)"
            ));
        }
    }

    private void hydrateAnimalTags(List<WorklistItemResponse> items) {
        Set<String> animalIds = items.stream()
                .map(WorklistItemResponse::getAnimalId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        if (animalIds.isEmpty()) {
            return;
        }

        Map<String, String> tagsByAnimalId = animalRepository.findAllById(animalIds)
                .stream()
                .collect(Collectors.toMap(AnimalEntity::getAnimalId, AnimalEntity::getTag, (first, second) -> first));

        for (WorklistItemResponse item : items) {
            if (item.getAnimalId() == null || item.getAnimalId().isBlank()) {
                continue;
            }
            if (item.getAnimalTag() != null && !item.getAnimalTag().isBlank()) {
                continue;
            }
            item.setAnimalTag(tagsByAnimalId.get(item.getAnimalId()));
        }
    }

    private Comparator<WorklistItemResponse> worklistComparator() {
        return Comparator
                .comparingInt((WorklistItemResponse item) -> priorityRank(item.getPriority()))
                .thenComparingInt(item -> dueStatusRank(item.getDueStatus()))
                .thenComparing(WorklistItemResponse::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> item.getAnimalId() == null ? "" : item.getAnimalId())
                .thenComparing(item -> item.getTaskId() == null ? "" : item.getTaskId());
    }

    private int priorityRank(WorklistPriority priority) {
        if (priority == WorklistPriority.HIGH) {
            return 0;
        }
        if (priority == WorklistPriority.MEDIUM) {
            return 1;
        }
        return 2;
    }

    private int dueStatusRank(WorklistDueStatus dueStatus) {
        if (dueStatus == WorklistDueStatus.OVERDUE) {
            return 0;
        }
        if (dueStatus == WorklistDueStatus.DUE_TODAY) {
            return 1;
        }
        if (dueStatus == WorklistDueStatus.DUE_SOON) {
            return 2;
        }
        return 3;
    }

    private WorklistItemResponse buildWorklistItem(
            String prefix,
            String sourceId,
            WorklistTaskType type,
            WorklistPriority priority,
            WorklistDueStatus dueStatus,
            LocalDate dueDate,
            String animalId,
            String title,
            String description
    ) {
        return WorklistItemResponse.builder()
                .taskId(buildWorklistTaskId(prefix, sourceId))
                .type(type)
                .priority(priority)
                .dueStatus(dueStatus)
                .dueDate(dueDate)
                .animalId(animalId)
                .sourceId(sourceId)
                .title(title)
                .description(description)
                .build();
    }

    private String buildWorklistTaskId(String prefix, String sourceId) {
        String safePrefix = (prefix == null ? "TASK" : prefix.trim()).replaceAll("[^A-Za-z0-9_-]", "_");
        String safeSource = (sourceId == null ? UUID.randomUUID().toString() : sourceId.trim())
                .replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeSource.isEmpty()) {
            safeSource = UUID.randomUUID().toString().substring(0, 8);
        }
        return "WK_" + safePrefix + "_" + safeSource;
    }

    private WorklistDueStatus dueStatus(LocalDate dueDate, LocalDate effectiveDate) {
        if (dueDate == null) {
            return WorklistDueStatus.INFO;
        }
        if (dueDate.isBefore(effectiveDate)) {
            return WorklistDueStatus.OVERDUE;
        }
        if (dueDate.isEqual(effectiveDate)) {
            return WorklistDueStatus.DUE_TODAY;
        }
        return WorklistDueStatus.DUE_SOON;
    }

    private AvgWindow averageBetween(Map<LocalDate, Double> dailyLiters, LocalDate fromDate, LocalDate toDate) {
        double total = 0d;
        long daysWithData = 0;

        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            Double liters = dailyLiters.get(cursor);
            if (liters != null) {
                total += liters;
                daysWithData++;
            }
            cursor = cursor.plusDays(1);
        }

        double avg = daysWithData == 0 ? 0d : total / daysWithData;
        return new AvgWindow(avg, daysWithData);
    }

    private boolean containsMastitisSignal(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            if (normalized.contains("mastitis")) {
                return true;
            }
        }
        return false;
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private int sanitizeWindowDays(Integer windowDays) {
        return windowDays == null ? 7 : Math.max(1, Math.min(30, windowDays));
    }

    private void validateAnimal(String animalId) {
        if (!animalRepository.existsById(animalId)) {
            throw new IllegalArgumentException("Animal not found for animalId");
        }
    }

    private String normalizeAnimalId(String animalId) {
        String normalized = animalId == null ? "" : animalId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("animalId is required");
        }
        return normalized;
    }

    private void validateDates(LocalDate doseDate, LocalDate nextDueDate, LocalDate boosterDueDate, LocalDate vaccineExpiryDate) {
        if (doseDate == null) {
            throw new IllegalArgumentException("doseDate is required");
        }
        if (nextDueDate != null && nextDueDate.isBefore(doseDate)) {
            throw new IllegalArgumentException("nextDueDate cannot be before doseDate");
        }
        if (boosterDueDate != null && boosterDueDate.isBefore(doseDate)) {
            throw new IllegalArgumentException("boosterDueDate cannot be before doseDate");
        }
        if (vaccineExpiryDate != null && vaccineExpiryDate.isBefore(doseDate)) {
            throw new IllegalArgumentException("vaccineExpiryDate cannot be before doseDate");
        }
    }

    private void validateBreedingDates(CreateBreedingEventRequest req) {
        LocalDate heatDate = req.getHeatDate();
        LocalDate inseminationDate = req.getInseminationDate();
        LocalDate pregnancyCheckDate = req.getPregnancyCheckDate();
        LocalDate expectedCalvingDate = req.getExpectedCalvingDate();
        LocalDate actualCalvingDate = req.getActualCalvingDate();

        if (heatDate == null) {
            throw new IllegalArgumentException("heatDate is required");
        }
        if (inseminationDate != null && inseminationDate.isBefore(heatDate)) {
            throw new IllegalArgumentException("inseminationDate cannot be before heatDate");
        }
        LocalDate checkFloorDate = inseminationDate != null ? inseminationDate : heatDate;
        if (pregnancyCheckDate != null && pregnancyCheckDate.isBefore(checkFloorDate)) {
            throw new IllegalArgumentException("pregnancyCheckDate cannot be before insemination/heat date");
        }
        if (expectedCalvingDate != null && expectedCalvingDate.isBefore(checkFloorDate)) {
            throw new IllegalArgumentException("expectedCalvingDate cannot be before insemination/heat date");
        }
        if (actualCalvingDate != null && actualCalvingDate.isBefore(heatDate)) {
            throw new IllegalArgumentException("actualCalvingDate cannot be before heatDate");
        }
    }

    private void validateWeight(Double weightAtDoseKg) {
        if (weightAtDoseKg == null) {
            return;
        }
        if (Double.isNaN(weightAtDoseKg) || Double.isInfinite(weightAtDoseKg) || weightAtDoseKg <= 0) {
            throw new IllegalArgumentException("weightAtDoseKg must be a positive finite number");
        }
    }

    private String trimRequired(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate resolveNextDueDate(String vaccineName, LocalDate doseDate, LocalDate requestedNextDueDate) {
        if (requestedNextDueDate != null || doseDate == null) {
            return requestedNextDueDate;
        }
        String key = normalizeVaccineKey(vaccineName);
        if (VACCINES_EVERY_6_MONTHS.contains(key)) {
            return doseDate.plusMonths(6);
        }
        if (VACCINES_EVERY_12_MONTHS.contains(key)) {
            return doseDate.plusYears(1);
        }
        // Brucellosis/Theileriosis commonly do not use simple repeat intervals; keep manual/null.
        if (BRUCELLOSIS_KEYS.contains(key) || THEILERIOSIS_KEYS.contains(key)) {
            return null;
        }
        return requestedNextDueDate;
    }

    private LocalDate resolveVaccinationDueDate(VaccinationEntity entity) {
        if (entity == null) {
            return null;
        }
        return resolveNextDueDate(entity.getVaccineName(), entity.getDoseDate(), entity.getNextDueDate());
    }

    private String normalizeVaccineKey(String vaccineName) {
        if (vaccineName == null) {
            return "";
        }
        String upper = vaccineName.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("HAEMORRHAGIC SEPTICAEMIA")) {
            return "HS";
        }
        if (upper.equals("BLACK QUARTER")) {
            return "BQ";
        }
        if (upper.equals("LUMPY SKIN DISEASE")) {
            return "LSD";
        }
        if (upper.equals("BOVINE BRUCELLOSIS")) {
            return "BRUCELLOSIS";
        }
        return upper;
    }

    private String buildVaccinationId() {
        return "VAC_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildDewormingId() {
        return "DWRM_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildBreedingEventId() {
        return "BRD_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private BreedingPregnancyResult defaultPregnancyResult(BreedingPregnancyResult value) {
        return value == null ? BreedingPregnancyResult.PENDING : value;
    }

    private BreedingCalfGender defaultCalfGender(BreedingCalfGender value) {
        return value == null ? BreedingCalfGender.UNKNOWN : value;
    }

    private BreedingCalvingOutcome defaultCalvingOutcome(BreedingCalvingOutcome value) {
        return value == null ? BreedingCalvingOutcome.UNKNOWN : value;
    }

    private VaccinationResponse toVaccinationResponse(VaccinationEntity entity) {
        return VaccinationResponse.builder()
                .vaccinationId(entity.getVaccinationId())
                .animalId(entity.getAnimalId())
                .vaccineName(entity.getVaccineName())
                .diseaseTarget(entity.getDiseaseTarget())
                .doseDate(entity.getDoseDate())
                .doseNumber(entity.getDoseNumber())
                .boosterDueDate(entity.getBoosterDueDate())
                .nextDueDate(entity.getNextDueDate())
                .vaccineExpiryDate(entity.getVaccineExpiryDate())
                .batchLotNo(entity.getBatchLotNo())
                .route(entity.getRoute())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DewormingResponse toDewormingResponse(DewormingEntity entity) {
        return DewormingResponse.builder()
                .dewormingId(entity.getDewormingId())
                .animalId(entity.getAnimalId())
                .drugName(entity.getDrugName())
                .doseDate(entity.getDoseDate())
                .nextDueDate(entity.getNextDueDate())
                .weightAtDoseKg(entity.getWeightAtDoseKg())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private BreedingEventResponse toBreedingEventResponse(BreedingEventEntity entity) {
        return BreedingEventResponse.builder()
                .breedingEventId(entity.getBreedingEventId())
                .animalId(entity.getAnimalId())
                .heatDate(entity.getHeatDate())
                .inseminationDate(entity.getInseminationDate())
                .sireTag(entity.getSireTag())
                .pregnancyCheckDate(entity.getPregnancyCheckDate())
                .pregnancyResult(entity.getPregnancyResult())
                .expectedCalvingDate(entity.getExpectedCalvingDate())
                .actualCalvingDate(entity.getActualCalvingDate())
                .calfAnimalId(entity.getCalfAnimalId())
                .calfTag(entity.getCalfTag())
                .calfGender(entity.getCalfGender())
                .calvingOutcome(entity.getCalvingOutcome())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static class AvgWindow {
        private final double avg;
        private final long days;

        private AvgWindow(double avg, long days) {
            this.avg = avg;
            this.days = days;
        }
    }

    private static class VaccinationDueCandidate {
        private final VaccinationEntity entity;
        private final LocalDate dueDate;

        private VaccinationDueCandidate(VaccinationEntity entity, LocalDate dueDate) {
            this.entity = entity;
            this.dueDate = dueDate;
        }
    }

    private static class BreedingProtocolCandidate {
        private final BreedingEventEntity entity;
        private final LocalDate dueDate;

        private BreedingProtocolCandidate(BreedingEventEntity entity, LocalDate dueDate) {
            this.entity = entity;
            this.dueDate = dueDate;
        }
    }
}
