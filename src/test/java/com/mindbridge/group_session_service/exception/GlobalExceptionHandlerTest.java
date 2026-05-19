package com.mindbridge.group_session_service.exception;

import com.mindbridge.group_session_service.controller.GroupSessionController;
import com.mindbridge.group_session_service.service.GroupSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupSessionController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupSessionService groupSessionService;

    @Test
    void cuandoResourceNotFoundException_debeRetornar404() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new ResourceNotFoundException("Sesión no encontrada"));

        mockMvc.perform(patch("/api/group-sessions/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void cuandoConflictException_debeRetornar409() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new ConflictException("La sesión ya está aprobada"));

        mockMvc.perform(patch("/api/group-sessions/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void cuandoValidationException_debeRetornar400() throws Exception {
        String dtoInvalido = """
                {
                    "title": "",
                    "description": "",
                    "psychologistId": null,
                    "scheduledAt": null,
                    "durationMinutes": null,
                    "maxParticipants": null
                }
                """;

        mockMvc.perform(post("/api/group-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dtoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void cuandoIllegalArgumentException_debeRetornar400() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new IllegalArgumentException("Argumento inválido"));

        mockMvc.perform(patch("/api/group-sessions/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void cuandoExcepcionGenerica_debeRetornar500() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new RuntimeException("Error inesperado"));

        mockMvc.perform(patch("/api/group-sessions/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }
}