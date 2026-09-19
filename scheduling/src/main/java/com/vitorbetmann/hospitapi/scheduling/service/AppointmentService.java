package com.vitorbetmann.hospitapi.scheduling.service;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import com.vitorbetmann.hospitapi.scheduling.domain.Patient;
import com.vitorbetmann.hospitapi.scheduling.domain.Role;
import com.vitorbetmann.hospitapi.scheduling.domain.User;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEvent;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEventType;
import com.vitorbetmann.hospitapi.scheduling.repository.AppointmentRepository;
import com.vitorbetmann.hospitapi.scheduling.repository.PatientRepository;
import com.vitorbetmann.hospitapi.scheduling.repository.UserRepository;
import com.vitorbetmann.hospitapi.scheduling.security.CurrentUser;
import com.vitorbetmann.hospitapi.scheduling.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int REASON_MAX_LENGTH = 255;
    private static final int NOTES_MAX_LENGTH = 2000;

    private final AppointmentRepository appointmentRepository;

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;


    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Appointment getById(Long id) {
        Appointment appointment = findAppointment(id);
        assertCanView(appointment.getPatient().getId());
        return appointment;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<Appointment> listByPatient(Long patientId, boolean onlyFuture) {
        assertCanView(patientId);
        if (onlyFuture) {
            return appointmentRepository.findByPatientIdAndScheduledAtAfterOrderByScheduledAtAsc(
                    patientId, OffsetDateTime.now(clock));
        }
        return appointmentRepository.findByPatientIdOrderByScheduledAtAsc(patientId);
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE')")
    @Transactional
    public Appointment create(CreateAppointmentInput input) {
        Appointment appointment = new Appointment();
        appointment.setPatient(findPatient(input.patientId()));
        appointment.setDoctor(findDoctor(input.doctorId()));
        appointment.reschedule(input.scheduledAt());
        appointment.setReason(requireMaxLength("reason", input.reason(), REASON_MAX_LENGTH));
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publishEvent(AppointmentEvent.of(AppointmentEventType.CREATED, saved, clock));
        return saved;
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE')")
    @Transactional
    public Appointment update(Long id, UpdateAppointmentInput input) {
        Appointment appointment = findAppointment(id);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new InvalidAppointmentException("Only SCHEDULED appointments can be updated");
        }
        if (input.doctorId() != null) {
            appointment.setDoctor(findDoctor(input.doctorId()));
        }
        if (input.scheduledAt() != null) {
            appointment.reschedule(input.scheduledAt());
        }
        if (input.status() != null) {
            appointment.setStatus(input.status());
        }
        if (input.reason() != null) {
            appointment.setReason(requireMaxLength("reason", input.reason(), REASON_MAX_LENGTH));
        }

        eventPublisher.publishEvent(AppointmentEvent.of(AppointmentEventType.UPDATED, appointment, clock));
        return appointment;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public Appointment updateClinicalNotes(Long id, String notes) {
        Appointment appointment = findAppointment(id);
        appointment.setNotes(requireMaxLength("notes", notes, NOTES_MAX_LENGTH));
        return appointment;
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new InvalidAppointmentException("No patient with id " + patientId));
    }

    private User findDoctor(Long doctorId) {
        return userRepository.findById(doctorId)
                .filter(user -> user.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new InvalidAppointmentException("No doctor with id " + doctorId));
    }

    private static String requireMaxLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidAppointmentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private void assertCanView(Long ownerPatientId) {
        SecurityUser caller = CurrentUser.get();
        if (caller.role() == Role.PATIENT && !ownerPatientId.equals(caller.patientId())) {
            throw new AccessDeniedException("Patients may only view their own appointments");
        }
    }
}