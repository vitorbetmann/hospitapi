package com.vitorbetmann.hospitapi.scheduling;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.*;


@IntegrationTest
class AppointmentServiceSecurityTest {

    @Autowired
    AppointmentService appointmentService;

    @Test
    @WithUserDetails("patient")
    void patientCanViewOwnAppointment() {
        assertThatNoException().isThrownBy(() -> appointmentService.getById(1L));
    }

    @Test
    @WithUserDetails("patient")
    void patientCannotViewAnotherPatientsAppointment() {
        assertThatThrownBy(() -> appointmentService.getById(3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithUserDetails("nurse")
    void nurseCanViewAnyAppointment() {
        assertThatNoException().isThrownBy(() -> appointmentService.getById(3L));
    }

    @Test
    void anonymousCallIsRejected() {
        assertThatThrownBy(() -> appointmentService.getById(1L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithUserDetails("patient")
    void patientCanListOwnAppointments() {
        assertThat(appointmentService.listByPatient(1L, false))
                .extracting(Appointment::getId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @WithUserDetails("patient")
    void patientCannotListAnotherPatientsAppointments() {
        assertThatThrownBy(() -> appointmentService.listByPatient(2L, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithUserDetails("nurse")
    void nurseCanListAnyPatientsAppointments() {
        assertThat(appointmentService.listByPatient(2L, false))
                .extracting(Appointment::getId)
                .containsExactly(3L);
    }
}