insert into patients (id, name, email, phone) overriding system value
values (1, 'Ana Souza', 'ana.souza@example.com', '+55 11 90000-0001'),
       (2, 'Bruno Lima', 'bruno.lima@example.com', '+55 11 90000-0002');

alter table patients alter column id restart with 100;

insert into users (id, username, password_hash, name, role, patient_id)
    overriding system value
values (1, 'doctor',
        '{bcrypt}$2a$10$BzvbfVun93GDAgK17yGrqef0Wy1YX9LGLWalge2wdeu2gMxa7WwNS',
        'Dr. Carla Mendes', 'DOCTOR', null),
       (2, 'nurse',
        '{bcrypt}$2a$10$w1djP5QgoMDOKYkjYzR8VObcbIRFf1kFwvuxPE/BhxVDg0mQFbs4e',
        'Nurse Diego Alves', 'NURSE', null),
       (3, 'patient',
        '{bcrypt}$2a$10$AK/LZ5lA0yFZQCKDPaVYr.875kbiSClhUurUzuZbPDDD5Y9qDqYxG',
        'Ana Souza', 'PATIENT', 1);

alter table users alter column id restart with 100;

insert into appointments (id, patient_id, doctor_id, scheduled_at, status,
                          reason, notes, created_at, updated_at)
    overriding system value
values (1, 1, 1, now() + interval '3 days', 'SCHEDULED',
        'Routine check-up', null, now(), now()),
       (2, 1, 1, now() - interval '30 days', 'COMPLETED',
        'Follow-up', 'Blood pressure stable.', now(), now()),
       (3, 2, 1, now() + interval '20 hours', 'SCHEDULED',
        'Post-surgery review', null, now(), now());

alter table appointments alter column id restart with 100;