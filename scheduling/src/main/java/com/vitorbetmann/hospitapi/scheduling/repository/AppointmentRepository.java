package com.vitorbetmann.hospitapi.scheduling.repository;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientIdOrderByScheduledAtAsc(Long patientId);
}
