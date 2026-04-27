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
import java.util.ArrayList;
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
@DisplayName("GroupSessionService — pruebas de concurrencia")
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

    // ═════════════════════════════════════════════════════════════════════════
    // Prueba 1: 10 hilos compiten — solo 3 deben inscribirse
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("🔒 10 hilos intentan inscribirse — solo se aceptan maxParticipants (3)")
    void soloDeberiaAceptarHastaMaxParticipants() throws InterruptedException {

        // ─── Estado compartido con acceso atómico ─────────────────────────────
        // El objeto 'lock' garantiza que count+save sean una operación atómica,
        // igual que lo haría el PESSIMISTIC_WRITE en la BD real.
        final Object lock = new Object();
        AtomicInteger enrolledCount = new AtomicInteger(0);

        List<UUID> patientIds = new ArrayList<>();
        for (int i = 0; i < TOTAL_THREADS; i++) {
            patientIds.add(UUID.randomUUID());
        }

        // findByIdWithLock: siempre devuelve la sesión
        lenient().when(groupSessionRepository.findByIdWithLock(sessionId))
                .thenReturn(Optional.of(approvedSession));

        // existsByGroupSessionIdAndPatientId: nadie duplicado
        lenient().when(enrollmentRepository.existsByGroupSessionIdAndPatientId(eq(sessionId), any(UUID.class)))
                .thenReturn(false);

        // countByGroupSessionId + save: sincronizados con el mismo lock
        // para simular correctamente el comportamiento del @Lock de JPA
        lenient().when(enrollmentRepository.countByGroupSessionId(sessionId))
                .thenAnswer(inv -> {
                    synchronized (lock) {
                        return (long) enrolledCount.get();
                    }
                });

        lenient().when(enrollmentRepository.save(any(GroupSessionEnrollment.class)))
                .thenAnswer(inv -> {
                    synchronized (lock) {
                        enrolledCount.incrementAndGet();
                        return inv.getArgument(0);
                    }
                });

        // ─── Lanzar 10 hilos simultáneos ─────────────────────────────────────
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(TOTAL_THREADS);

        List<String> successes  = new CopyOnWriteArrayList<>();
        List<String> rejections = new CopyOnWriteArrayList<>();

        for (int i = 0; i < TOTAL_THREADS; i++) {
            final UUID patientId = patientIds.get(i);
            final int  threadNum = i + 1;

            executor.submit(() -> {
                try {
                    startGate.await();
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

        startGate.countDown(); // ¡arranca la carrera!
        endGate.await();
        executor.shutdown();

        // ─── Log de resultados ────────────────────────────────────────────────
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
                .as("No deben aceptarse más inscripciones que maxParticipants")
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);

        assertThat(successes.size() + rejections.size())
                .as("Todos los hilos deben terminar (éxito o rechazo)")
                .isEqualTo(TOTAL_THREADS);

        assertThat(enrolledCount.get())
                .as("El contador real de inscritos no debe superar maxParticipants")
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Prueba 2: sesión ya llena — todos los hilos son rechazados
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("🔒 Sesión ya llena — todos los hilos son rechazados")
    void todosDeberianRechazarseSiSesionLlena() throws InterruptedException {

        lenient().when(groupSessionRepository.findByIdWithLock(sessionId))
                .thenReturn(Optional.of(approvedSession));
        lenient().when(enrollmentRepository.existsByGroupSessionIdAndPatientId(eq(sessionId), any()))
                .thenReturn(false);
        lenient().when(enrollmentRepository.countByGroupSessionId(sessionId))
                .thenReturn((long) MAX_PARTICIPANTS); // ya llena desde el inicio

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