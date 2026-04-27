package com.mindbridge.group_session_service.controller;

import com.mindbridge.group_session_service.dto.EnrollRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionResponseDTO;
import com.mindbridge.group_session_service.service.GroupSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/group-sessions")
@RequiredArgsConstructor
@Tag(name = "Group Sessions", description = "Gestión de sesiones grupales de terapia")
public class GroupSessionController {

    private final GroupSessionService groupSessionService;

    // ─── POST /api/group-sessions ─────────────────────────────────────────────
    @Operation(
            summary = "Solicitar una sesión grupal",
            description = "El psicólogo solicita una nueva sesión grupal. Queda en estado PENDING hasta que un admin la apruebe."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sesión creada exitosamente",
                    content = @Content(schema = @Schema(implementation = GroupSessionResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos", content = @Content)
    })
    @PostMapping
    public ResponseEntity<GroupSessionResponseDTO> requestSession(
            @Valid @RequestBody GroupSessionRequestDTO dto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(groupSessionService.requestSession(dto));
    }

    // ─── PATCH /api/group-sessions/{id}/approve ───────────────────────────────
    @Operation(
            summary = "Aprobar una sesión grupal",
            description = "El admin aprueba una sesión en estado PENDING. Pasa a APPROVED y se vuelve visible para pacientes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión aprobada exitosamente",
                    content = @Content(schema = @Schema(implementation = GroupSessionResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La sesión no está en estado PENDING", content = @Content)
    })
    @PatchMapping("/{id}/approve")
    public ResponseEntity<GroupSessionResponseDTO> approveSession(
            @Parameter(description = "UUID de la sesión a aprobar", required = true)
            @PathVariable UUID id) {
        return ResponseEntity.ok(groupSessionService.approveSession(id));
    }

    // ─── GET /api/group-sessions/approved ────────────────────────────────────
    @Operation(
            summary = "Listar sesiones aprobadas",
            description = "El paciente consulta todas las sesiones en estado APPROVED disponibles para inscribirse."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de sesiones disponibles",
                    content = @Content(schema = @Schema(implementation = GroupSessionResponseDTO.class)))
    })
    @GetMapping("/approved")
    public ResponseEntity<List<GroupSessionResponseDTO>> getApprovedSessions() {
        return ResponseEntity.ok(groupSessionService.getApprovedSessions());
    }

    // ─── POST /api/group-sessions/{id}/enroll ────────────────────────────────
    @Operation(
            summary = "Inscribirse en una sesión",
            description = "El paciente se inscribe en una sesión APPROVED. Falla si ya está inscrito o si la sesión está llena."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripción exitosa",
                    content = @Content(schema = @Schema(implementation = GroupSessionResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Paciente ya inscrito o sesión llena", content = @Content)
    })
    @PostMapping("/{id}/enroll")
    public ResponseEntity<GroupSessionResponseDTO> enrollPatient(
            @Parameter(description = "UUID de la sesión", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody EnrollRequestDTO dto) {
        return ResponseEntity.ok(groupSessionService.enrollPatient(id, dto));
    }

    // ─── PATCH /api/group-sessions/{id}/cancel ────────────────────────────────
    @Operation(
            summary = "Cancelar una sesión grupal",
            description = "El admin o el psicólogo cancela una sesión que no esté COMPLETED ni ya CANCELLED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión cancelada exitosamente",
                    content = @Content(schema = @Schema(implementation = GroupSessionResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La sesión no puede cancelarse en su estado actual", content = @Content)
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<GroupSessionResponseDTO> cancelSession(
            @Parameter(description = "UUID de la sesión a cancelar", required = true)
            @PathVariable UUID id) {
        return ResponseEntity.ok(groupSessionService.cancelSession(id));
    }

    @Operation(
            summary = "Eliminar una sesión grupal",
            description = "Elimina permanentemente una sesión. No se permite eliminar sesiones en estado APPROVED — cancélalas primero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión eliminada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "No se puede eliminar una sesión APPROVED", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @Parameter(description = "UUID de la sesión a eliminar", required = true)
            @PathVariable UUID id) {
        groupSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}