package com.mindbridge.group_session_service.dto;

import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "DTO de respuesta con los datos de una sesión grupal")
public record GroupSessionResponseDTO(

        @Schema(description = "ID único de la sesión")
        UUID id,

        @Schema(description = "Nombre de la sesión")
        String title,

        @Schema(description = "Descripción de la sesión")
        String description,

        @Schema(description = "UUID del psicólogo responsable")
        UUID psychologistId,

        @Schema(description = "Fecha y hora programada")
        LocalDateTime scheduledAt,

        @Schema(description = "Duración en minutos")
        Integer durationMinutes,

        @Schema(description = "Número máximo de participantes")
        Integer maxParticipants,

        @Schema(description = "Plazas disponibles restantes")
        long availableSpots,

        @Schema(description = "Estado actual de la sesión")
        GroupSessionStatus status,

        @Schema(description = "Versión para optimistic locking")
        Integer version
) {}