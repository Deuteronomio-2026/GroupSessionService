package com.mindbridge.group_session_service.repository;

import com.mindbridge.group_session_service.model.entity.GroupSessionEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupSessionEnrollmentRepository extends JpaRepository<GroupSessionEnrollment, UUID> {

    boolean existsByGroupSessionIdAndPatientId(UUID groupSessionId, UUID patientId);

    long countByGroupSessionId(UUID groupSessionId);

    List<GroupSessionEnrollment> findByGroupSessionId(UUID groupSessionId);
}
