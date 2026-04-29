package com.mindbridge.group_session_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "group_session_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollment_session_patient",
                columnNames = {"group_session_id", "patient_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSessionEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_session_id", nullable = false)
    private GroupSession groupSession;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private LocalDateTime enrolledAt;
}