# CLAUDE.md – hospitapi

Hospital scheduling backend for the POSTECH Java Tech Challenge, Phase 3.
This file and `docs/decisions.md` are the source of truth. If a request
conflicts with them, stop and ask instead of silently changing course.

## Architecture
Monorepo, one standalone Maven project per service (no root pom), shared
root `docker-compose.yml`.

- `scheduling/` (artifact `hospitapi-scheduling`): Spring Security (HTTP Basic,
  DB-backed users), Spring for GraphQL, JPA + PostgreSQL, Flyway. Publishes
  appointment events to RabbitMQ after the transaction commits.
- `notification/` (artifact `hospitapi-notification`): consumes appointment
  events and logs/mocks reminders. Optional `@Scheduled` job for appointments
  in the next 24h.
- `history/` (artifact `hospitapi-history`): optional stretch goal. Do not
  start it until all mandatory parts work.
- Broker: RabbitMQ, with JSON messages.

## Stack
- Java 21, **Spring Boot 4.1.1**, Maven (each service's own wrapper), Jar packaging
- Group `com.vitorbetmann`. Packages `com.vitorbetmann.hospitapi.scheduling`
  and `com.vitorbetmann.hospitapi.notification`
- `application.properties` only (no YAML)
- PostgreSQL, Flyway, Spring Data JPA, Spring AMQP, Actuator, Lombok,
  Testcontainers
- Infrastructure is started with `docker compose up` from the repo root.
  The `spring-boot-docker-compose` dependency is intentionally not used.

### Spring Boot 4 notes (don't write 3.x-style code by habit)
- Jackson 3 is the default: packages are `tools.jackson.*`, not
  `com.fasterxml.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`).
  Don't use Jackson 2-specific classes such as `Jackson2JsonMessageConverter`.
- Starters and test starters are more modular than in 3.x. Prefer the ones
  Initializr generated, and check the Boot 4 docs before adding dependencies.
- Spring Framework 7, Jakarta EE 11, JSpecify null-safety annotations.
- When unsure whether an API changed in 4.x, say so and check. Don't guess.

## Domain
- User: id, username, passwordHash, role (DOCTOR | NURSE | PATIENT),
  patientId (nullable, only for PATIENT)
- Patient: id, name, email, phone
- Appointment: id, patientId, doctorId, dateTime,
  status (SCHEDULED | COMPLETED | CANCELLED), reason, notes, createdAt, updatedAt
- AppointmentEvent: eventId, type (CREATED | UPDATED), appointmentId,
  patientName, patientEmail, dateTime, occurredAt

## Access rules
- DOCTOR and NURSE: create, update, and view appointments.
- Only DOCTOR edits clinical `notes`.
- PATIENT: views only their own appointments. Enforce this with an
  ownership check in the service layer, not only in the resolver.
- These rules resolve an ambiguity in the brief and must be documented in the README.

## Conventions
- Code, identifiers, comments, and commits in English. The README may be in Portuguese.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
- Layering per service: `api` (GraphQL controllers), `service`, `domain`,
  `repository`, `config`, `messaging`.
- Constructor injection only (no field `@Autowired`).
- Schema changes go through Flyway migrations only (`ddl-auto: validate`).
  Never edit a migration that has already been applied; add a new one.
- New decisions go in `docs/decisions.md` using its entry template, with the
  next free ID. Never renumber or delete entries; supersede them.

## Dev environment
- Development alternates between macOS and Windows. The machines stay in sync
  only through GitHub: pull before working, push when done. The repo must
  not live in a cloud-synced folder (OneDrive, iCloud Desktop/Documents).
- Maven wrapper: `./mvnw` on macOS, Git Bash, Linux, and in containers;
  `mvnw.cmd` (or `.\mvnw` in PowerShell) on Windows.
- Line endings are LF for all text files (enforced by `.gitattributes`); only
  `*.cmd`/`*.bat` use CRLF. Don't introduce CRLF files.
- `mvnw` must stay executable in git (mode `100755`).
- Renames that only change letter case must use `git mv` (macOS and Windows
  filesystems are case-insensitive; Linux containers are not).
- Docker images must support both amd64 and arm64.

## Commands
```bash
docker compose up -d                        # Postgres + RabbitMQ (from Part 1)
cd scheduling && ./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
cd notification && ./mvnw spring-boot:run
./mvnw test                                 # inside a service folder
```

## Parts (progress)
- [X] 0. Setup: Initializr projects, repo, CLAUDE.md, docs/, push to GitHub
- [ ] 1. Infrastructure: docker-compose, application.yml profiles, apps start and connect
- [ ] 2. Domain & persistence: entities, repositories, Flyway, seed data
- [ ] 3. Security: SecurityFilterChain, UserDetailsService, PasswordEncoder, @PreAuthorize, ownership
- [ ] 4. GraphQL: schema, queries/mutations, onlyFuture filter, error handling
- [ ] 5. Messaging: exchange/queue/binding, publish after commit, consumer, retries/DLQ
- [ ] 6. Scheduled reminders (extra)
- [ ] 7. Tests: unit + Testcontainers integration (GraphQL, RabbitMQ)
- [ ] 8. Deliverables: README, Postman collection per role incl. denials
- [ ] 9. (Optional) hospitapi-history