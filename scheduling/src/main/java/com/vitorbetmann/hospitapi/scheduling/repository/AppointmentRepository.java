package com.vitorbetmann.hospitapi.scheduling.repository;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}