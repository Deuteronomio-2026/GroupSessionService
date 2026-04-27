package com.mindbridge.group_session_service.repository;

import com.mindbridge.group_session_service.model.entity.GroupSession;
import com.mindbridge.group_session_service.model.enums.GroupSessionStatus;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupSessionRepository extends JpaRepository<GroupSession, UUID> {
    List<GroupSession> findByStatus(GroupSessionStatus status) ;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM GroupSession o WHERE o.id = :id")
    Optional<GroupSession> findByIdWithLock(@Param("id") UUID id);

}
