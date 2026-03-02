package net.nani.dairy.employees.dto;

import lombok.*;
import net.nani.dairy.employees.SalaryComputationMode;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAttendanceMonthlyReportResponse {
    private String month;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private boolean includeInactive;
    private SalaryComputationMode salaryMode;
    private double fullTimeDailyRate;
    private double partTimeDailyRate;
    private double fullTimeShiftRate;
    private double partTimeShiftRate;
    private double hourlyRate;
    private double overtimeHourlyRate;
    private double standardHoursPerDay;
    private int totalEmployees;
    private int totalPresentDays;
    private int totalAbsentDays;
    private int totalPresentShifts;
    private int totalAbsentShifts;
    private double totalHoursWorked;
    private double totalOvertimeHours;
    private double totalSuggestedSalary;
    private List<EmployeeAttendanceMonthlyRowResponse> rows;
}
