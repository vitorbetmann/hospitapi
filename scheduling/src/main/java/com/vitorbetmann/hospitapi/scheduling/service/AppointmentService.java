package com.vitorbetmann.hospitapi.scheduling.service;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.Role;
import com.vitorbetmann.hospitapi.scheduling.repository.AppointmentRepository;
import com.vitorbetmann.hospitapi.scheduling.security.CurrentUser;
import com.vitorbetmann.hospitapi.scheduling.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Appointment getById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
        assertCanView(appointment.getPatient().getId());
        return appointment;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<Appointment> listByPatient(Long patientId) {
        assertCanView(patientId);
        return appointmentRepository.findByPatientIdOrderByScheduledAtAsc(patientId);
    }

    private void assertCanView(Long ownerPatientId) {
        SecurityUser caller = CurrentUser.get();
        if (caller.role() == Role.PATIENT && !ownerPatientId.equals(caller.patientId())) {
            throw new AccessDeniedException("Patients may only view their own appointments");
        }
    }
}