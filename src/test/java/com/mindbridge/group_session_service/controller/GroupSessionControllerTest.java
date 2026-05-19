package com.mindbridge.group_session_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mindbridge.group_session_service.dto.EnrollRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionResponseDTO;
import com.mindbridge.group_session_service.exception.ConflictException;
import com.mindbridge.group_session_service.exception.ResourceNotFoundException;
import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import com.mindbridge.group_session_service.service.GroupSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupSessionController.class)
class GroupSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupSessionService groupSessionService;

    private ObjectMapper objectMapper;
    private GroupSessionResponseDTO sessionResponse;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sessionId = UUID.randomUUID();
        sessionResponse = new GroupSessionResponseDTO(
                sessionId,
                "Terapia grupal",
                "Descripción test",
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(1),
                60,
                10,
                10,
                GroupSessionStatus.PENDING,
                0
        );
    }

    @Test
    void requestSession_debeRetornar201() throws Exception {
        GroupSessionRequestDTO dto = new GroupSessionRequestDTO(
                "Terapia grupal",
                "Descripción test",
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(1),
                60,
                10
        );

        when(groupSessionService.requestSession(any())).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/group-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Terapia grupal"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void approveSession_debeRetornar200() throws Exception {
        GroupSessionResponseDTO approved = new GroupSessionResponseDTO(
                sessionId, "Terapia grupal", "Descripción test",
                UUID.randomUUID(), LocalDateTime.now().plusDays(1),
                60, 10, 10, GroupSessionStatus.APPROVED, 0
        );

        when(groupSessionService.approveSession(sessionId)).thenReturn(approved);

        mockMvc.perform(patch("/api/group-sessions/" + sessionId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveSession_cuandoNoExiste_debeRetornar404() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new ResourceNotFoundException("Sesión no encontrada"));

        mockMvc.perform(patch("/api/group-sessions/" + sessionId + "/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveSession_cuandoNoEstaPending_debeRetornar409() throws Exception {
        when(groupSessionService.approveSession(any()))
                .thenThrow(new ConflictException("La sesión no está en estado PENDING"));

        mockMvc.perform(patch("/api/group-sessions/" + sessionId + "/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void getApprovedSessions_debeRetornarLista() throws Exception {
        when(groupSessionService.getApprovedSessions()).thenReturn(List.of(sessionResponse));

        mockMvc.perform(get("/api/group-sessions/approved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Terapia grupal"));
    }

    @Test
    void getSessionsByPsychologist_debeRetornarLista() throws Exception {
        UUID psychologistId = UUID.randomUUID();
        when(groupSessionService.getPsychologistSessions(psychologistId))
                .thenReturn(List.of(sessionResponse));

        mockMvc.perform(get("/api/group-sessions/psychologist/" + psychologistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Terapia grupal"));
    }

    @Test
    void getAllSessions_debeRetornarLista() throws Exception {
        when(groupSessionService.getAllGroupSession()).thenReturn(List.of(sessionResponse));

        mockMvc.perform(get("/api/group-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Terapia grupal"));
    }

    @Test
    void enrollPatient_debeRetornar200() throws Exception {
        EnrollRequestDTO dto = new EnrollRequestDTO(UUID.randomUUID());

        when(groupSessionService.enrollPatient(any(), any())).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/group-sessions/" + sessionId + "/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void enrollPatient_cuandoSesionLlena_debeRetornar409() throws Exception {
        EnrollRequestDTO dto = new EnrollRequestDTO(UUID.randomUUID());

        when(groupSessionService.enrollPatient(any(), any()))
                .thenThrow(new ConflictException("La sesión está llena"));

        mockMvc.perform(post("/api/group-sessions/" + sessionId + "/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelSession_debeRetornar200() throws Exception {
        GroupSessionResponseDTO cancelled = new GroupSessionResponseDTO(
                sessionId, "Terapia grupal", "Descripción test",
                UUID.randomUUID(), LocalDateTime.now().plusDays(1),
                60, 10, 10, GroupSessionStatus.CANCELLED, 0
        );

        when(groupSessionService.cancelSession(sessionId)).thenReturn(cancelled);

        mockMvc.perform(patch("/api/group-sessions/" + sessionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelSession_cuandoYaEstaCancelada_debeRetornar409() throws Exception {
        when(groupSessionService.cancelSession(any()))
                .thenThrow(new ConflictException("La sesión ya está cancelada"));

        mockMvc.perform(patch("/api/group-sessions/" + sessionId + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteSession_debeRetornar204() throws Exception {
        doNothing().when(groupSessionService).deleteSession(sessionId);

        mockMvc.perform(delete("/api/group-sessions/" + sessionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSession_cuandoNoExiste_debeRetornar404() throws Exception {
        doThrow(new ResourceNotFoundException("Sesión no encontrada"))
                .when(groupSessionService).deleteSession(any());

        mockMvc.perform(delete("/api/group-sessions/" + sessionId))
                .andExpect(status().isNotFound());
    }
}