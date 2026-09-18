package com.vitorbetmann.hospitapi.scheduling.api;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.service.AppointmentService;
import com.vitorbetmann.hospitapi.scheduling.service.CreateAppointmentInput;
import com.vitorbetmann.hospitapi.scheduling.service.UpdateAppointmentInput;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @QueryMapping
    public Appointment appointment(@Argument Long id) {
        return appointmentService.getById(id);
    }

    @QueryMapping
    public List<Appointment> appointmentsByPatient(@Argument Long patientId, @Argument Boolean onlyFuture) {
        return appointmentService.listByPatient(patientId, Boolean.TRUE.equals(onlyFuture));
    }

    @MutationMapping
    public Appointment createAppointment(@Argument CreateAppointmentInput input) {
        return appointmentService.create(input);
    }

    @MutationMapping
    public Appointment updateAppointment(@Argument Long id, @Argument UpdateAppointmentInput input) {
        return appointmentService.update(id, input);
    }

    @MutationMapping
    public Appointment updateClinicalNotes(@Argument Long id, @Argument String notes) {
        return appointmentService.updateClinicalNotes(id, notes);
    }
}