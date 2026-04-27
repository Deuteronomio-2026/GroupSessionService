package com.mindbridge.group_session_service;

import com.mindbridge.group_session_service.dto.EnrollRequestDTO;
import com.mindbridge.group_session_service.exception.ConflictException;
import com.mindbridge.group_session_service.model.entity.GroupSession;
import com.mindbridge.group_session_service.model.entity.GroupSessionEnrollment;
import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import com.mindbridge.group_session_service.repository.GroupSessionEnrollmentRepository;
import com.mindbridge.group_session_service.repository.GroupSessionRepository;
import com.mindbridge.group_session_service.service.GroupSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupSessionService — prueba de concurrencia")
class GroupSessionConcurrencyTest {

    @Mock
    private GroupSessionRepository groupSessionRepository;

    @Mock
    private GroupSessionEnrollmentRepository enrollmentRepository;

    @InjectMocks
    private GroupSessionService groupSessionService;

    private static final int MAX_PARTICIPANTS = 3;
    private static final int TOTAL_THREADS    = 10;

    private UUID sessionId;
    private GroupSession approvedSession;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        approvedSession = GroupSession.builder()
                .id(sessionId)
                .title("Sesión concurrente")
                .description("Test de concurrencia")
                .psychologistId(UUID.randomUUID())
                .scheduledAt(LocalDateTime.now().plusDays(3))
                .durationMinutes(60)
                .maxParticipants(MAX_PARTICIPANTS)
                .status(GroupSessionStatus.APPROVED)
                .version(0)
                .build();
    }

    @Test
    @DisplayName("🔒 10 hilos intentan inscribirse — solo se aceptan maxParticipants (3)")
    void soloDeberiaAceptarHastaMAxParticipants() throws InterruptedException {

        // ─── Contador compartido que simula la BD ────────────────────────────
        AtomicInteger enrolledCount = new AtomicInteger(0);

        // Cada hilo tiene su propio patientId — nadie duplicado
        List<UUID> patientIds = new java.util.ArrayList<>();
        for (int i = 0; i < TOTAL_THREADS; i++) {
            patientIds.add(UUID.randomUUID());
        }

        // ─── Mocks sincronizados ─────────────────────────────────────────────

        // findByIdWithLock siempre devuelve la sesión
        when(groupSessionRepository.findByIdWithLock(sessionId))
                .thenReturn(Optional.of(approvedSession));

        // Ningún paciente está duplicado
        when(enrollmentRepository.existsByGroupSessionIdAndPatientId(eq(sessionId), any(UUID.class)))
                .thenReturn(false);

        // countByGroupSessionId lee el estado real del contador compartido
        when(enrollmentRepository.countByGroupSessionId(sessionId))
                .thenAnswer(inv -> (long) enrolledCount.get());

        // save incrementa el contador y devuelve el enrollment
        when(enrollmentRepository.save(any(GroupSessionEnrollment.class)))
                .thenAnswer(inv -> {
                    enrolledCount.incrementAndGet();
                    return inv.getArgument(0);
                });

        // ─── Lanzar 10 hilos simultáneos ─────────────────────────────────────
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CountDownLatch startGate = new CountDownLatch(1); // todos arrancan juntos
        CountDownLatch endGate   = new CountDownLatch(TOTAL_THREADS);

        List<String> successes = new CopyOnWriteArrayList<>();
        List<String> rejections = new CopyOnWriteArrayList<>();

        for (int i = 0; i < TOTAL_THREADS; i++) {
            final UUID patientId = patientIds.get(i);
            final int threadNum  = i + 1;

            executor.submit(() -> {
                try {
                    startGate.await(); // esperar a que todos estén listos
                    groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(patientId));
                    successes.add("Hilo-" + threadNum + " inscrito ✅");
                } catch (ConflictException e) {
                    rejections.add("Hilo-" + threadNum + " rechazado ❌ — " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();        // ¡arranca la carrera!
        endGate.await();              // esperar que todos terminen
        executor.shutdown();

        // ─── Resultados ───────────────────────────────────────────────────────
        System.out.println("\n═══ Resultado de concurrencia ══════════════════════");
        System.out.println("MaxParticipants : " + MAX_PARTICIPANTS);
        System.out.println("Hilos totales   : " + TOTAL_THREADS);
        System.out.println("Inscripciones   : " + successes.size());
        System.out.println("Rechazos        : " + rejections.size());
        System.out.println("────────────────────────────────────────────────────");
        successes.forEach(System.out::println);
        rejections.forEach(System.out::println);
        System.out.println("════════════════════════════════════════════════════\n");

        // ─── Assertions ───────────────────────────────────────────────────────
        assertThat(successes.size())
                .as("No se deben aceptar más inscripciones que maxParticipants")
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);

        assertThat(successes.size() + rejections.size())
                .as("Todos los hilos deben haber terminado (éxito o rechazo)")
                .isEqualTo(TOTAL_THREADS);

        assertThat(enrolledCount.get())
                .as("El contador real de inscritos no debe superar maxParticipants")
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);
    }

    @Test
    @DisplayName("🔒 Sesión ya llena — todos los hilos son rechazados")
    void todosDeberianRechazarseSiSesionLlena() throws InterruptedException {

        // La sesión ya tiene maxParticipants inscritos
        when(groupSessionRepository.findByIdWithLock(sessionId))
                .thenReturn(Optional.of(approvedSession));
        when(enrollmentRepository.existsByGroupSessionIdAndPatientId(eq(sessionId), any()))
                .thenReturn(false);
        when(enrollmentRepository.countByGroupSessionId(sessionId))
                .thenReturn((long) MAX_PARTICIPANTS); // ya llena

        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(TOTAL_THREADS);

        List<String> rejections = new CopyOnWriteArrayList<>();

        for (int i = 0; i < TOTAL_THREADS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    groupSessionService.enrollPatient(sessionId, new EnrollRequestDTO(UUID.randomUUID()));
                } catch (ConflictException e) {
                    rejections.add(e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        executor.shutdown();

        assertThat(rejections)
                .as("Todos los hilos deben ser rechazados si la sesión ya está llena")
                .hasSize(TOTAL_THREADS);

        verify(enrollmentRepository, never()).save(any());
    }
}