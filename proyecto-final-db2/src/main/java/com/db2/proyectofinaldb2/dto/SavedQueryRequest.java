package com.db2.proyectofinaldb2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SavedQueryRequest(
        @JsonProperty("nombre") @NotBlank @Size(max = 100) String name,
        @JsonProperty("descripcion") @Size(max = 500) String description,
        @JsonProperty("motor") @NotBlank String engine,
        @JsonProperty("codigo") @NotBlank @Size(max = 20000) String code
) {
}
