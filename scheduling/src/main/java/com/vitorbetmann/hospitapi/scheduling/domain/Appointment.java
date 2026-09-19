package com.vitorbetmann.hospitapi.scheduling.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    /** Changed only through {@link #reschedule(OffsetDateTime)}. */
    @Setter(AccessLevel.NONE)
    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(length = 255)
    private String reason;

    @Column(length = 2000)
    private String notes;

    /**
     * When the 24-hour reminder job claimed this appointment (D-040).
     * Null means no reminder has been sent for the current scheduledAt.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Sets or moves the appointment time. A reminder already sent for the old
     * time no longer applies, so it is cleared and the job may claim the
     * appointment again (D-040). Compares instants, not offsets: the same
     * moment expressed with a different offset is not a change.
     */
    public void reschedule(OffsetDateTime newScheduledAt) {
        if (scheduledAt == null || !newScheduledAt.isEqual(scheduledAt)) {
            scheduledAt = newScheduledAt;
            reminderSentAt = null;
        }
    }

    /** Records that the reminder job has claimed this appointment (D-040). */
    public void markReminderSent(OffsetDateTime at) {
        reminderSentAt = at;
    }
}