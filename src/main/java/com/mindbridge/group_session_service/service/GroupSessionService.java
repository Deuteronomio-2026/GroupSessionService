package com.mindbridge.group_session_service.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupSessionService {

    private final GroupSessionRepository groupSessionRepository;
    private final GroupSessionEnrollmentRepository enrollmentRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    // ─── Historia 1: Psicólogo solicita sesión ───────────────────────────────

    @Transactional
    public GroupSessionResponseDTO requestSession(GroupSessionRequestDTO dto) {
        GroupSession session = GroupSession.builder()
                .title(dto.title())
                .description(dto.description())
                .psychologistId(dto.psychologistId())
                .scheduledAt(dto.scheduledAt())
                .durationMinutes(dto.durationMinutes())
                .maxParticipants(dto.maxParticipants())
                .status(GroupSessionStatus.PENDING)
                .build();

        GroupSession saved = groupSessionRepository.save(session);

        rabbitTemplate.convertAndSend(exchange, "session.requested", toResponseDTO(saved, 0L));

        return toResponseDTO(saved, 0L);
    }

    // ─── Historia 2: Admin aprueba sesión ────────────────────────────────────

    @Transactional
    public GroupSessionResponseDTO approveSession(UUID id) {
        // Pessimistic lock para evitar aprobaciones concurrentes
        GroupSession session = groupSessionRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sesión grupal no encontrada con id: " + id));

        if (session.getStatus() != GroupSessionStatus.PENDING) {
            throw new ConflictException(
                    "Solo se pueden aprobar sesiones en estado PENDING. Estado actual: " + session.getStatus());
        }

        session.setStatus(GroupSessionStatus.APPROVED);
        GroupSession updated = groupSessionRepository.save(session);
        long enrolled = enrollmentRepository.countByGroupSessionId(id);

        // Publicar evento
        rabbitTemplate.convertAndSend(exchange, "session.approved", toResponseDTO(updated, enrolled));

        return toResponseDTO(updated, enrolled);
    }

    // ─── Historia 3: Paciente ve sesiones disponibles ────────────────────────

    @Transactional(readOnly = true)
    public List<GroupSessionResponseDTO> getApprovedSessions() {
        return groupSessionRepository.findByStatus(GroupSessionStatus.APPROVED)
                .stream()
                .map(session -> {
                    long enrolled = enrollmentRepository.countByGroupSessionId(session.getId());
                    return toResponseDTO(session, enrolled);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GroupSessionResponseDTO> getPsychologistSessions(UUID psychologistId) {
        return groupSessionRepository.findByPsychologistId(psychologistId)
                .stream()
                .map(session -> {
                    long enrolled = enrollmentRepository.countByGroupSessionId(session.getId());
                    return toResponseDTO(session, enrolled);
                })
                .collect(Collectors.toList());
    }

    public List<GroupSessionResponseDTO> getAllGroupSession(){
        return groupSessionRepository.findAll()
                .stream()
                .map(session -> {
                    long enrolled = enrollmentRepository.countByGroupSessionId(session.getId());
                    return toResponseDTO(session, enrolled);
                })
                .collect(Collectors.toList());
    }

    // ─── Historia 4: Paciente se inscribe ────────────────────────────────────

    @Transactional
    public GroupSessionResponseDTO enrollPatient(UUID sessionId, EnrollRequestDTO dto) {
        // Pessimistic lock para controlar concurrencia en inscripciones
        GroupSession session = groupSessionRepository.findByIdWithLock(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sesión grupal no encontrada con id: " + sessionId));

        if (session.getStatus() != GroupSessionStatus.APPROVED) {
            throw new ConflictException(
                    "Solo se puede inscribir en sesiones APPROVED. Estado actual: " + session.getStatus());
        }

        if (enrollmentRepository.existsByGroupSessionIdAndPatientId(sessionId, dto.patientId())) {
            throw new ConflictException(
                    "El paciente ya está inscrito en esta sesión.");
        }

        long currentEnrolled = enrollmentRepository.countByGroupSessionId(sessionId);
        if (currentEnrolled >= session.getMaxParticipants()) {
            throw new ConflictException(
                    "La sesión está llena. Máximo de participantes alcanzado: " + session.getMaxParticipants());
        }

        GroupSessionEnrollment enrollment = GroupSessionEnrollment.builder()
                .groupSession(session)
                .patientId(dto.patientId())
                .enrolledAt(LocalDateTime.now())
                .build();

        enrollmentRepository.save(enrollment);

        //  Publicar evento
        rabbitTemplate.convertAndSend(exchange, "session.enrolled", toResponseDTO(session, currentEnrolled + 1));
        return toResponseDTO(session, currentEnrolled + 1);
    }

    // ─── Cancelar sesión (psicólogo o admin) ─────────────────────────────────

    @Transactional
    public GroupSessionResponseDTO cancelSession(UUID id) {
        GroupSession session = groupSessionRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sesión grupal no encontrada con id: " + id));

        if (session.getStatus() == GroupSessionStatus.COMPLETED) {
            throw new ConflictException(
                    "No se puede cancelar una sesión ya completada.");
        }
        if (session.getStatus() == GroupSessionStatus.CANCELLED) {
            throw new ConflictException(
                    "La sesión ya está cancelada.");
        }

        session.setStatus(GroupSessionStatus.CANCELLED);
        GroupSession updated = groupSessionRepository.save(session);
        long enrolled = enrollmentRepository.countByGroupSessionId(id);

        rabbitTemplate.convertAndSend(exchange, "session.cancelled", toResponseDTO(updated, enrolled));
        return toResponseDTO(updated, enrolled);
    }

    @Transactional
    public void deleteSession(UUID id) {
        GroupSession session = groupSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sesión grupal no encontrada con id: " + id));

        if (session.getStatus() == GroupSessionStatus.APPROVED) {
            throw new ConflictException(
                    "No se puede eliminar una sesión APPROVED. Cancélala primero.");
        }

        enrollmentRepository.deleteAll(
                enrollmentRepository.findByGroupSessionId(id)
        );
        groupSessionRepository.delete(session);
    }



    // ─── Mapper interno ──────────────────────────────────────────────────────

    private GroupSessionResponseDTO toResponseDTO(GroupSession session, long enrolled) {
        long availableSpots = session.getMaxParticipants() - enrolled;
        return new GroupSessionResponseDTO(
                session.getId(),
                session.getTitle(),
                session.getDescription(),
                session.getPsychologistId(),
                session.getScheduledAt(),
                session.getDurationMinutes(),
                session.getMaxParticipants(),
                Math.max(availableSpots, 0),
                session.getStatus(),
                session.getVersion()
        );
    }
}