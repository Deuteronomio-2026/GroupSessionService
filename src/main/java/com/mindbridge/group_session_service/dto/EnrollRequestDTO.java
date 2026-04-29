package com.mindbridge.group_session_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "DTO para inscribir a un paciente en una sesión grupal")
public record EnrollRequestDTO(

        @NotNull(message = "El ID del paciente es obligatorio")
        @Schema(description = "UUID del paciente que desea inscribirse", example = "f1e2d3c4-b5a6-7890-abcd-ef0987654321")
        UUID patientId
) {}