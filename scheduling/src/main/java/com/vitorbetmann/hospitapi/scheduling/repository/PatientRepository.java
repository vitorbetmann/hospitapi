package com.vitorbetmann.hospitapi.scheduling.repository;

import com.vitorbetmann.hospitapi.scheduling.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
}
