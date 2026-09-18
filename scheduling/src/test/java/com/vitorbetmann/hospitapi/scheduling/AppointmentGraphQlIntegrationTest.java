package com.vitorbetmann.hospitapi.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;

/**
 * GraphQL tests against the real schema, controllers, security and database.
 * Seeded rows (patients 1-2, appointments 1-3) are only read here; anything
 * this class creates belongs to test patient 900, so other classes sharing
 * the context always see unchanged seed data.
 */
@IntegrationTest
@Sql(scripts = "/sql/test-patient.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AppointmentGraphQlIntegrationTest {

    private static final String DOCTOR_ID = "1";
    private static final String NURSE_ID = "2";
    private static final String TEST_PATIENT_ID = "900";
    private static final String FUTURE_DATE_TIME = "2030-01-15T10:30:00-03:00";

    private static final String APPOINTMENT = """
            query($id: ID!) {
              appointment(id: $id) { id status scheduledAt patient { id } }
            }
            """;

    private static final String APPOINTMENTS_BY_PATIENT = """
            query($patientId: ID!, $onlyFuture: Boolean) {
              appointmentsByPatient(patientId: $patientId, onlyFuture: $onlyFuture) { id }
            }
            """;

    private static final String CREATE = """
            mutation($input: CreateAppointmentInput!) {
              createAppointment(input: $input) { id status scheduledAt }
            }
            """;

    private static final String UPDATE = """
            mutation($id: ID!, $input: UpdateAppointmentInput!) {
              updateAppointment(id: $id, input: $input) { id }
            }
            """;

    private static final String UPDATE_NOTES = """
            mutation($id: ID!, $notes: String!) {
              updateClinicalNotes(id: $id, notes: $notes) { id notes }
            }
            """;

    @Autowired
    ExecutionGraphQlServiceTester graphQlTester;

    // --- Authorization ---

    @Test
    @WithUserDetails("patient")
    void patientCanQueryOwnAppointment() {
        graphQlTester.document(APPOINTMENT).variable("id", "1")
                .execute()
                .path("appointment.patient.id").entity(String.class).isEqualTo("1");
    }

    @Test
    @WithUserDetails("patient")
    void patientGetsForbiddenForAnotherPatientsAppointment() {
        graphQlTester.document(APPOINTMENT).variable("id", "3")
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.FORBIDDEN))
                .path("appointment").valueIsNull();
    }

    @Test
    void anonymousRequestIsUnauthorized() {
        graphQlTester.document(APPOINTMENT).variable("id", "1")
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.UNAUTHORIZED))
                .path("appointment").valueIsNull();
    }

    @Test
    @WithUserDetails("nurse")
    void nurseCannotUpdateClinicalNotes() {
        graphQlTester.document(UPDATE_NOTES)
                .variable("id", "2")
                .variable("notes", "Should never be saved.")
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.FORBIDDEN))
                .path("updateClinicalNotes").valueIsNull();
    }

    @Test
    @WithUserDetails("doctor")
    void doctorCanUpdateClinicalNotes() {
        String id = createTestAppointment();

        graphQlTester.document(UPDATE_NOTES)
                .variable("id", id)
                .variable("notes", "Patient reports mild headaches.")
                .execute()
                .path("updateClinicalNotes.notes").entity(String.class)
                .isEqualTo("Patient reports mild headaches.");
    }

    // --- Error mapping ---

    @Test
    @WithUserDetails("doctor")
    void unknownAppointmentIsNotFound() {
        graphQlTester.document(APPOINTMENT).variable("id", "999999")
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.NOT_FOUND))
                .path("appointment").valueIsNull();
    }

    @Test
    @WithUserDetails("nurse")
    void creatingWithNonDoctorIsBadRequest() {
        graphQlTester.document(CREATE)
                .variable("input", createInput(NURSE_ID, FUTURE_DATE_TIME))
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.BAD_REQUEST))
                .path("createAppointment").valueIsNull();
    }

    @Test
    @WithUserDetails("doctor")
    void updatingNonScheduledAppointmentIsBadRequest() {
        // Seeded appointment 2 is COMPLETED.
        graphQlTester.document(UPDATE)
                .variable("id", "2")
                .variable("input", Map.of("reason", "Rescheduled"))
                .execute()
                .errors().satisfy(errors -> assertSingleError(errors, ErrorType.BAD_REQUEST))
                .path("updateAppointment").valueIsNull();
    }

    // --- onlyFuture ---

    @Test
    @WithUserDetails("patient")
    void onlyFutureExcludesPastAppointments() {
        // Appointment 2 is 30 days in the past, appointment 1 is 3 days ahead.
        assertThat(listIds("1", false)).containsExactly("2", "1");
        assertThat(listIds("1", true)).containsExactly("1");
    }

    @Test
    @WithUserDetails("nurse")
    void onlyFutureUsesInjectedClock() {
        // Appointment 3 is 20h ahead of real time but behind the test clock
        // (+1 day), so it only counts as past if the service uses the Clock.
        assertThat(listIds("2", false)).containsExactly("3");
        assertThat(listIds("2", true)).isEmpty();
    }

    // --- DateTime scalar (D-032 guard) ---

    @Test
    @WithUserDetails("nurse")
    void dateTimeRoundTripsThroughMutationAndQuery() {
        OffsetDateTime sent = OffsetDateTime.parse(FUTURE_DATE_TIME);

        GraphQlTester.Response created = graphQlTester.document(CREATE)
                .variable("input", createInput(DOCTOR_ID, FUTURE_DATE_TIME))
                .execute();
        created.path("createAppointment.status").entity(String.class).isEqualTo("SCHEDULED");
        String id = created.path("createAppointment.id").entity(String.class).get();
        String returned = created.path("createAppointment.scheduledAt").entity(String.class).get();

        String reloaded = graphQlTester.document(APPOINTMENT).variable("id", id)
                .execute()
                .path("appointment.scheduledAt").entity(String.class).get();

        // Compare instants, not strings: Postgres stores UTC, so the offset
        // of a reloaded value may differ from the one sent.
        assertThat(OffsetDateTime.parse(returned)).isAtSameInstantAs(sent);
        assertThat(OffsetDateTime.parse(reloaded)).isAtSameInstantAs(sent);
    }

    // --- Helpers ---

    record IdOnly(String id) {
    }

    private List<String> listIds(String patientId, boolean onlyFuture) {
        return graphQlTester.document(APPOINTMENTS_BY_PATIENT)
                .variable("patientId", patientId)
                .variable("onlyFuture", onlyFuture)
                .execute()
                .path("appointmentsByPatient").entityList(IdOnly.class).get()
                .stream().map(IdOnly::id).toList();
    }

    private String createTestAppointment() {
        return graphQlTester.document(CREATE)
                .variable("input", createInput(DOCTOR_ID, FUTURE_DATE_TIME))
                .execute()
                .path("createAppointment.id").entity(String.class).get();
    }

    private static Map<String, Object> createInput(String doctorId, String scheduledAt) {
        return Map.of(
                "patientId", TEST_PATIENT_ID,
                "doctorId", doctorId,
                "scheduledAt", scheduledAt,
                "reason", "Integration test");
    }

    private static void assertSingleError(List<ResponseError> errors, ErrorType expected) {
        assertThat(errors).singleElement()
                .extracting(ResponseError::getErrorType)
                .isEqualTo(expected);
    }
}