package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.health.WorklistDueStatus;
import net.nani.dairy.health.WorklistPriority;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthProtocolItemResponse {
    private String protocolId;
    private String code;
    private String category;
    private String title;
    private String description;
    private WorklistPriority priority;
    private WorklistDueStatus dueStatus;
    private LocalDate dueDate;
}
