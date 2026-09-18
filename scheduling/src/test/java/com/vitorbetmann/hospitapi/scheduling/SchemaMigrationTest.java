package com.vitorbetmann.hospitapi.scheduling;

import com.vitorbetmann.hospitapi.scheduling.repository.AppointmentRepository;
import com.vitorbetmann.hospitapi.scheduling.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private UserRepository users;

    @Test
    void migrationsApplyAndEntitiesMatchTheSchema() {
        assertThat(users.count()).isEqualTo(3);
        assertThat(appointments.count()).isEqualTo(3);
    }
}