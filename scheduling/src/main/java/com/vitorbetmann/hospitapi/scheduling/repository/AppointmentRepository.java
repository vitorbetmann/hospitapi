package com.vitorbetmann.hospitapi.scheduling.repository;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findById(Long id);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByPatientIdOrderByScheduledAtAsc(Long patientId);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByPatientIdAndScheduledAtAfterOrderByScheduledAtAsc(
            Long patientId, OffsetDateTime after);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select a from Appointment a
            where a.status = :status
              and a.reminderSentAt is null
              and a.scheduledAt > :now
              and a.scheduledAt <= :until
            order by a.scheduledAt
            """)
    List<Appointment> lockDueForReminder(AppointmentStatus status,
                                         OffsetDateTime now,
                                         OffsetDateTime until);
}