package net.nani.dairy.employees.dto;

import lombok.*;
import net.nani.dairy.employees.EmployeeType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAttendanceMonthlyRowResponse {
    private String employeeId;
    private String employeeName;
    private EmployeeType employeeType;
    private boolean active;
    private int workingDaysInMonth;
    private int presentDays;
    private int absentDays;
    private int presentShifts;
    private int absentShifts;
    private int shiftsMarked;
    private double totalHoursWorked;
    private double avgHoursPerPresentDay;
    private double overtimeHours;
    private double suggestedSalary;
}
