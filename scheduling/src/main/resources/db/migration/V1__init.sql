create table patients
(
    id    bigint generated always as identity primary key,
    name  varchar(100) not null,
    email varchar(255) not null,
    phone varchar(30)
);

create table users
(
    id            bigint generated always as identity primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null,
    name          varchar(100) not null,
    role          varchar(20)  not null,
    patient_id    bigint references patients (id),
    constraint users_role_check
        check (role in ('DOCTOR', 'NURSE', 'PATIENT')),
    constraint users_patient_link_check
        check ((role = 'PATIENT' and patient_id is not null)
            or (role <> 'PATIENT' and patient_id is null))
);

create table appointments
(
    id           bigint generated always as identity primary key,
    patient_id   bigint      not null references patients (id),
    doctor_id    bigint      not null references users (id),
    scheduled_at timestamptz not null,
    status       varchar(20) not null,
    reason       varchar(255),
    notes        varchar(2000),
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    constraint appointments_status_check
        check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
);

create index idx_appointments_patient_scheduled_at
    on appointments (patient_id, scheduled_at);
create index idx_appointments_doctor_scheduled_at
    on appointments (doctor_id, scheduled_at);