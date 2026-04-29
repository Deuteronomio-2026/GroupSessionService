package com.mindbridge.group_session_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "DTO para solicitar una nueva sesión grupal")
public record GroupSessionRequestDTO(

        @NotBlank(message = "El título es obligatorio")
        @Size(min = 3, max = 100, message = "El título debe tener entre 3 y 100 caracteres")
        @Schema(description = "Nombre de la sesión", example = "Terapia de manejo del estrés")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        @Schema(description = "Descripción de la sesión", example = "Sesión grupal para trabajar técnicas de relajación")
        String description,

        @NotNull(message = "El ID del psicólogo es obligatorio")
        @Schema(description = "UUID del psicólogo solicitante", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID psychologistId,

        @NotNull(message = "La fecha y hora son obligatorias")
        @Future(message = "La sesión debe programarse en el futuro")
        @Schema(description = "Fecha y hora programada", example = "2025-06-15T10:00:00")
        LocalDateTime scheduledAt,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 15, message = "La duración mínima es de 15 minutos")
        @Max(value = 480, message = "La duración máxima es de 480 minutos (8 horas)")
        @Schema(description = "Duración en minutos", example = "60")
        Integer durationMinutes,

        @NotNull(message = "El número máximo de participantes es obligatorio")
        @Min(value = 2, message = "Se requieren al menos 2 participantes")
        @Max(value = 50, message = "No se pueden tener más de 50 participantes")
        @Schema(description = "Número máximo de participantes", example = "10")
        Integer maxParticipants
) {}