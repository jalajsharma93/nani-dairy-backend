package net.nani.dairy.animals.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalGenealogyResponse {
    private AnimalResponse animal;
    private AnimalResponse mother;
    private AnimalResponse sire;
    private List<AnimalResponse> offspring;
    private int offspringCount;
    private int activeOffspringCount;
}
