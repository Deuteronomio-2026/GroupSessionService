package com.mindbridge.group_session_service;

import com.mindbridge.group_session_service.dto.EnrollRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionRequestDTO;
import com.mindbridge.group_session_service.dto.GroupSessionResponseDTO;
import com.mindbridge.group_session_service.exception.ConflictException;
import com.mindbridge.group_session_service.exception.ResourceNotFoundException;
import com.mindbridge.group_session_service.model.entity.GroupSession;
import com.mindbridge.group_session_service.model.entity.GroupSessionEnrollment;
import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import com.mindbridge.group_session_service.repository.GroupSessionEnrollmentRepository;
import com.mindbridge.group_session_service.repository.GroupSessionRepository;
import com.mindbridge.group_session_service.service.GroupSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupSessionService — pruebas unitarias")
class GroupSessionServiceTest {

    @Mock
    private GroupSessionRepository groupSessionRepository;

    @Mock
    private GroupSessionEnrollmentRepository enrollmentRepository;

    @InjectMocks
    private GroupSessionService groupSessionService;

    private UUID sessionId;
    private UUID psychologistId;
    private UUID patientId;
    private GroupSession pendingSession;
    private GroupSession approvedSession;

    @BeforeEach
    void setUp() {
        sessionId      = UUID.randomUUID();
        psychologistId = UUID.randomUUID();
        patientId      = UUID.randomUUID();

        pendingSession = GroupSession.builder()
                .id(sessionId)
                .title("Terapia de ansiedad")
                .description("Sesión grupal para manejo de ansiedad")
                .psychologistId(psychologistId)
                .scheduledAt(LocalDateTime.now().plusDays(7))
                .durationMinutes(60)
                .maxParticipants(5)
                .status(GroupSessionStatus.PENDING)
                .version(0)
                .build();

        approvedSession = GroupSession.builder()
                .id(sessionId)
                .title("Terapia de ansiedad")
                .description("Sesión grupal para manejo de ansiedad")
                .psychologistId(psychologistId)
                .scheduledAt(LocalDateTime.now().plusDays(7))
                .durationMinutes(60)
                .maxParticipants(5)
                .status(GroupSessionStatus.APPROVED)
                .version(1)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. requestSession()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("requestSession()")
    class RequestSession {

        @Test
        @DisplayName("✅ Crea sesión en estado PENDING con los datos correctos")
        void deberiaCrearSesionEnEstadoPending() {
            GroupSessionRequestDTO dto = new GroupSessionRequestDTO(
                    "Terapia de ansiedad",
                    "Sesión grupal para manejo de ansiedad",
                    psychologistId,
                    LocalDateTime.now().plusDays(7),
                    60,
                    5
            );

            when(groupSessionRepository.save(any(GroupSession.class)))
                    .thenReturn(pendingSession);

            GroupSessionResponseDTO result = groupSessionService.requestSession(dto);

            assertThat(result.status()).isEqualTo(GroupSessionStatus.PENDING);
            assertThat(result.title()).isEqualTo("Terapia de ansiedad");
            assertThat(result.availableSpots()).isEqualTo(5);
            verify(groupSessionRepository).save(any(GroupSession.class));
        }

        @Test
        @DisplayName("✅ availableSpots refleja maxParticipants cuando no hay inscritos")
        void availableSpotsSinInscritos() {
            GroupSessionRequestDTO dto = new GroupSessionRequestDTO(
                    "Terapia", "Desc", psychologistId,
                    LocalDateTime.now().plusDays(1), 30, 10
            );
            GroupSession session = GroupSession.builder()
                    .id(sessionId).title("Terapia").description("Desc")
                    .psychologistId(psychologistId)
                    .scheduledAt(LocalDateTime.now().plusDays(1))
                    .durationMinutes(30).maxParticipants(10)
                    .status(GroupSessionStatus.PENDING).version(0).build();

            when(groupSessionRepository.save(any())).thenReturn(session);

            GroupSessionResponseDTO result = groupSessionService.requestSession(dto);

            assertThat(result.availableSpots()).isEqualTo(10);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. approveSession()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("approveSession()")
    class ApproveSession {

        @Test
        @DisplayName("✅ Aprueba correctamente una sesión PENDING")
        void deberiaAprobarSesionPending() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(pendingSession));
            when(groupSessionRepository.save(any())).thenReturn(approvedSession);
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(0L);

            GroupSessionResponseDTO result = groupSessionService.approveSession(sessionId);

            assertThat(result.status()).isEqualTo(GroupSessionStatus.APPROVED);
            verify(groupSessionRepository).save(pendingSession);
        }

        @Test
        @DisplayName("❌ Lanza ResourceNotFoundException si la sesión no existe")
        void deberiaLanzarExcepcionSiNoExiste() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupSessionService.approveSession(sessionId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(sessionId.toString());
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión ya está APPROVED")
        void deberiaLanzarConflictSiYaEstaAprobada() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(approvedSession));

            assertThatThrownBy(() -> groupSessionService.approveSession(sessionId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión está CANCELLED")
        void deberiaLanzarConflictSiEstaCancelada() {
            GroupSession cancelled = GroupSession.builder()
                    .id(sessionId).status(GroupSessionStatus.CANCELLED).build();

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> groupSessionService.approveSession(sessionId))
                    .isInstanceOf(ConflictException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. getApprovedSessions()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getApprovedSessions()")
    class GetApprovedSessions {

        @Test
        @DisplayName("✅ Retorna lista de sesiones APPROVED con spots calculados")
        void deberiaRetornarSesionesAprobadas() {
            when(groupSessionRepository.findByStatus(GroupSessionStatus.APPROVED))
                    .thenReturn(List.of(approvedSession));
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(2L);

            List<GroupSessionResponseDTO> result = groupSessionService.getApprovedSessions();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(GroupSessionStatus.APPROVED);
            assertThat(result.get(0).availableSpots()).isEqualTo(3); // 5 max - 2 inscritos
        }

        @Test
        @DisplayName("✅ Retorna lista vacía si no hay sesiones aprobadas")
        void deberiaRetornarListaVacia() {
            when(groupSessionRepository.findByStatus(GroupSessionStatus.APPROVED))
                    .thenReturn(List.of());

            List<GroupSessionResponseDTO> result = groupSessionService.getApprovedSessions();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("✅ availableSpots nunca es negativo aunque haya más inscritos que max")
        void availableSpotsMinimoEsCero() {
            when(groupSessionRepository.findByStatus(GroupSessionStatus.APPROVED))
                    .thenReturn(List.of(approvedSession));
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(10L);

            List<GroupSessionResponseDTO> result = groupSessionService.getApprovedSessions();

            assertThat(result.get(0).availableSpots()).isZero();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. enrollPatient()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("enrollPatient()")
    class EnrollPatient {

        @Test
        @DisplayName("✅ Inscribe correctamente a un paciente en sesión APPROVED con cupo")
        void deberiaInscribirPacienteCorrectamente() {
            EnrollRequestDTO dto = new EnrollRequestDTO(patientId);

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(approvedSession));
            when(enrollmentRepository.existsByGroupSessionIdAndPatientId(sessionId, patientId))
                    .thenReturn(false);
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(2L);
            when(enrollmentRepository.save(any(GroupSessionEnrollment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            GroupSessionResponseDTO result = groupSessionService.enrollPatient(sessionId, dto);

            assertThat(result.availableSpots()).isEqualTo(2); // 5 - (2+1)
            verify(enrollmentRepository).save(any(GroupSessionEnrollment.class));
        }

        @Test
        @DisplayName("❌ Lanza ResourceNotFoundException si la sesión no existe")
        void deberiaLanzarNotFoundSiSesionNoExiste() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(patientId)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión no está APPROVED")
        void deberiaLanzarConflictSiSesionNoPending() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(pendingSession));

            assertThatThrownBy(() -> groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(patientId)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si el paciente ya está inscrito")
        void deberiaLanzarConflictSiPacienteYaInscrito() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(approvedSession));
            when(enrollmentRepository.existsByGroupSessionIdAndPatientId(sessionId, patientId))
                    .thenReturn(true);

            assertThatThrownBy(() -> groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(patientId)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya está inscrito");
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión está llena")
        void deberiaLanzarConflictSiSesionLlena() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(approvedSession));
            when(enrollmentRepository.existsByGroupSessionIdAndPatientId(sessionId, patientId))
                    .thenReturn(false);
            when(enrollmentRepository.countByGroupSessionId(sessionId))
                    .thenReturn(5L);

            assertThatThrownBy(() -> groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(patientId)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("llena");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. cancelSession()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancelSession()")
    class CancelSession {

        @Test
        @DisplayName("✅ Cancela correctamente una sesión PENDING")
        void deberiaCancelarSesionPending() {
            GroupSession cancelled = GroupSession.builder()
                    .id(sessionId).title("Terapia").description("Desc")
                    .psychologistId(psychologistId)
                    .scheduledAt(LocalDateTime.now().plusDays(7))
                    .durationMinutes(60).maxParticipants(5)
                    .status(GroupSessionStatus.CANCELLED).version(1).build();

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(pendingSession));
            when(groupSessionRepository.save(any())).thenReturn(cancelled);
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(0L);

            GroupSessionResponseDTO result = groupSessionService.cancelSession(sessionId);

            assertThat(result.status()).isEqualTo(GroupSessionStatus.CANCELLED);
        }

        @Test
        @DisplayName("✅ Cancela correctamente una sesión APPROVED")
        void deberiaCancelarSesionApproved() {
            GroupSession cancelled = GroupSession.builder()
                    .id(sessionId).status(GroupSessionStatus.CANCELLED)
                    .maxParticipants(5).build();

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(approvedSession));
            when(groupSessionRepository.save(any())).thenReturn(cancelled);
            when(enrollmentRepository.countByGroupSessionId(sessionId)).thenReturn(3L);

            GroupSessionResponseDTO result = groupSessionService.cancelSession(sessionId);

            assertThat(result.status()).isEqualTo(GroupSessionStatus.CANCELLED);
        }

        @Test
        @DisplayName("❌ Lanza ResourceNotFoundException si la sesión no existe")
        void deberiaLanzarNotFoundSiNoExiste() {
            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupSessionService.cancelSession(sessionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión ya está CANCELLED")
        void deberiaLanzarConflictSiYaCancelada() {
            GroupSession cancelled = GroupSession.builder()
                    .id(sessionId).status(GroupSessionStatus.CANCELLED).build();

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> groupSessionService.cancelSession(sessionId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya está cancelada");
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si la sesión está COMPLETED")
        void deberiaLanzarConflictSiEstaCompleted() {
            GroupSession completed = GroupSession.builder()
                    .id(sessionId).status(GroupSessionStatus.COMPLETED).build();

            when(groupSessionRepository.findByIdWithLock(sessionId))
                    .thenReturn(Optional.of(completed));

            assertThatThrownBy(() -> groupSessionService.cancelSession(sessionId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("completada");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. deleteSession()
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteSession()")
    class DeleteSession {

        @Test
        @DisplayName("✅ Elimina correctamente una sesión PENDING")
        void deberiaEliminarSesionPending() {
            when(groupSessionRepository.findById(sessionId))
                    .thenReturn(Optional.of(pendingSession));
            when(enrollmentRepository.findByGroupSessionId(sessionId))
                    .thenReturn(List.of());

            assertThatCode(() -> groupSessionService.deleteSession(sessionId))
                    .doesNotThrowAnyException();

            verify(groupSessionRepository).delete(pendingSession);
        }

        @Test
        @DisplayName("✅ Elimina inscripciones antes de eliminar la sesión")
        void deberiaEliminarInscripcionesAntes() {
            GroupSessionEnrollment enrollment = GroupSessionEnrollment.builder()
                    .id(UUID.randomUUID()).groupSession(pendingSession)
                    .patientId(patientId).enrolledAt(LocalDateTime.now()).build();

            when(groupSessionRepository.findById(sessionId))
                    .thenReturn(Optional.of(pendingSession));
            when(enrollmentRepository.findByGroupSessionId(sessionId))
                    .thenReturn(List.of(enrollment));

            groupSessionService.deleteSession(sessionId);

            verify(enrollmentRepository).deleteAll(List.of(enrollment));
            verify(groupSessionRepository).delete(pendingSession);
        }

        @Test
        @DisplayName("❌ Lanza ResourceNotFoundException si la sesión no existe")
        void deberiaLanzarNotFoundSiNoExiste() {
            when(groupSessionRepository.findById(sessionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupSessionService.deleteSession(sessionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("❌ Lanza ConflictException si intenta eliminar sesión APPROVED")
        void deberiaLanzarConflictSiEstaAprobada() {
            when(groupSessionRepository.findById(sessionId))
                    .thenReturn(Optional.of(approvedSession));

            assertThatThrownBy(() -> groupSessionService.deleteSession(sessionId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("APPROVED");
        }
    }
}