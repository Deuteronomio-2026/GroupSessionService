package com.mindbridge.group_session_service;

import com.mindbridge.group_session_service.dto.EnrollRequestDTO;
import com.mindbridge.group_session_service.exception.ConflictException;
import com.mindbridge.group_session_service.model.entity.GroupSession;
import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import com.mindbridge.group_session_service.repository.GroupSessionEnrollmentRepository;
import com.mindbridge.group_session_service.repository.GroupSessionRepository;
import com.mindbridge.group_session_service.service.GroupSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("GroupSessionService — Concurrency Integration Test")
class GroupSessionConcurrencyIT {

    @Autowired
    private GroupSessionService groupSessionService;

    @Autowired
    private GroupSessionRepository groupSessionRepository;

    @Autowired
    private GroupSessionEnrollmentRepository enrollmentRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private static final int MAX_PARTICIPANTS = 3;
    private static final int TOTAL_THREADS = 10;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        groupSessionRepository.deleteAll();

        GroupSession session = GroupSession.builder()
                .title("Sesión concurrente")
                .description("Test real")
                .psychologistId(UUID.randomUUID())
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .maxParticipants(MAX_PARTICIPANTS)
                .status(GroupSessionStatus.APPROVED)
                .build();

        sessionId = groupSessionRepository.save(session).getId();
    }

    @Test
    @DisplayName("🔒 Concurrencia real — solo se aceptan maxParticipants")
    void shouldAllowOnlyMaxParticipants() throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(TOTAL_THREADS);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < TOTAL_THREADS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();

                    groupSessionService.enrollPatient(
                            sessionId,
                            new EnrollRequestDTO(UUID.randomUUID())
                    );

                    success.incrementAndGet();

                } catch (ConflictException e) {
                    rejected.incrementAndGet();
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

        long finalCount = enrollmentRepository.countByGroupSessionId(sessionId);

        System.out.println("\n═══ RESULTADO REAL ═══");
        System.out.println("Success: " + success.get());
        System.out.println("Rejected: " + rejected.get());
        System.out.println("DB Count: " + finalCount);
        System.out.println("=====================\n");

        assertThat(success.get())
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);

        assertThat(finalCount)
                .isLessThanOrEqualTo(MAX_PARTICIPANTS);

        assertThat(success.get() + rejected.get())
                .isEqualTo(TOTAL_THREADS);
    }
}