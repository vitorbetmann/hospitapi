# CLAUDE.md – hospitapi

Hospital scheduling backend for the POSTECH Java Tech Challenge, Phase 3.
This file and `docs/decisions.md` are the source of truth. If a request
conflicts with them, stop and ask instead of silently changing course.

## Architecture

Monorepo, one standalone Maven project per service (no root pom), shared
root `compose.yaml`.

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
- Infrastructure (Postgres + RabbitMQ) is started with `docker compose up -d`
  from the repo root. `compose.yaml` contains infrastructure only during
  development (D-018); services run on the host with `./mvnw spring-boot:run`
  — scheduling on 8080, notification on 8081. The `spring-boot-docker-compose`
  dependency is intentionally not used.

### Spring Boot 4 notes (don't write 3.x-style code by habit)

- Jackson 3 is the default: packages are `tools.jackson.*`, not
  `com.fasterxml.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`).
  Don't use Jackson 2-specific classes such as `Jackson2JsonMessageConverter`.
- Starters are modular: most technologies have a dedicated starter plus a
  matching `spring-boot-starter-*-test` companion. Add the test starter for
  the slice being tested (e.g. `spring-boot-starter-data-jpa-test`); a missing
  one shows up as a context that silently lacks the beans you expect. Never
  add `spring-boot-starter-classic` / `spring-boot-starter-test-classic` —
  they exist to ease 3.x migrations and defeat the modular layout.
- The modular `*-test` starters pull in `spring-boot-starter-test`
  transitively (JUnit Jupiter 6, AssertJ, Mockito + mockito-junit-jupiter,
  Hamcrest, Awaitility). Verified in Part 1. Don't declare it explicitly.
- Test API changes: `@SpringBootTest` no longer provides MockMvc (add
  `@AutoConfigureMockMvc`); `@MockBean` is gone (use Spring Framework 7's
  `@MockitoBean`); `MockitoTestExecutionListener` was removed, so plain
  `@Mock`/`@Captor` need Mockito's own `MockitoExtension`.
- Spring Framework 7, Jakarta EE 11, JSpecify null-safety annotations.
- When unsure whether an API changed in 4.x, say so and check. Don't guess.

### Version notes (most examples online are older)

- **Testcontainers 2.x**: modules are prefixed — `testcontainers-postgresql`,
  `testcontainers-rabbitmq`, `testcontainers-junit-jupiter`. The 1.x
  coordinates (`org.testcontainers:postgresql`) do not resolve.
- Container classes moved out of `org.testcontainers.containers` into a
  per-module package: `org.testcontainers.postgresql.PostgreSQLContainer`,
  `org.testcontainers.rabbitmq.RabbitMQContainer`.
- Many container classes lost their generic type parameter: write
  `PostgreSQLContainer`, not `PostgreSQLContainer<?>`.
- Testcontainers dropped JUnit 4 support. No `@Rule` / `@ClassRule` fields.
  Versions come from Boot's dependency management; never pin them.
- **JUnit 6.0.3**, not JUnit 5. Everyday annotations are unchanged, but
  suspect this first when a snippet from a tutorial won't compile.

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
- Schema changes go through Flyway migrations only (`ddl-auto=validate`).
  Never edit a migration that has already been applied; add a new one.
- Never create scratch or hand-made tables in the `hospitapi` database.
  `baselineOnMigrate` stays `false` (D-021), so Flyway fails at startup on a
  non-empty schema with no history table. Experiment in a throwaway database
  or a Testcontainers instance instead.
- New decisions go in `docs/decisions.md` using its entry template, with the
  next free ID. Never renumber or delete entries; supersede them.

## Dev environment

- Development alternates between macOS and Windows. The machines stay in sync
  only through GitHub: pull before working, push when done. The repo must
  not live in a cloud-synced folder (OneDrive, iCloud Desktop/Documents).
- Maven wrapper: `./mvnw` on macOS, Git Bash, Linux, and in containers;
  `mvnw.cmd` (or `.\mvnw` in PowerShell) on Windows.
- Maven commands run from inside a service folder; the repo root has no pom (D-001). In zsh, quote `-D` arguments
  containing `*`
  (`-Dincludes='org.mockito:*'`), or zsh expands the glob and fails.
- Line endings are LF for all text files (enforced by `.gitattributes`); only
  `*.cmd`/`*.bat` use CRLF. Don't introduce CRLF files.
- `mvnw` must stay executable in git (mode `100755`).
- Renames that only change letter case must use `git mv` (macOS and Windows
  filesystems are case-insensitive; Linux containers are not).
- Docker images must support both amd64 and arm64.

## Commands

```bash
docker compose up -d                        # Postgres + RabbitMQ (infrastructure only)
docker compose down                         # stop; data survives in named volumes
docker compose down -v                      # WIPES pgdata — Flyway re-runs from V1
cd scheduling && ./mvnw spring-boot:run     # :8080  (Windows: mvnw.cmd spring-boot:run)
cd notification && ./mvnw spring-boot:run   # :8081
curl localhost:8080/actuator/health         # db + rabbit UP
curl localhost:8081/actuator/health         # rabbit UP (no db by design)
./mvnw test                                 # inside a service folder; Docker, no compose
./mvnw spring-boot:test-run                 # run against throwaway containers
```

RabbitMQ management UI: `localhost:15672`.

## Testing

- Integration tests start their own Postgres and RabbitMQ via Testcontainers (D-022). `./mvnw test` needs Docker running
  but **not** `docker compose up`.
- `@ServiceConnection` contributes a `ConnectionDetails` bean, which Boot
  prefers over `spring.datasource.*` / `spring.rabbitmq.*` properties — tests
  never reach the compose containers.
- Container image tags in `TestcontainersConfiguration` must match
  `compose.yaml` (`postgres:18-alpine`, `rabbitmq:4-management-alpine`).
  Never `:latest`.
- No embedded database: `@DataJpaTest` needs
  `@AutoConfigureTestDatabase(replace = Replace.NONE)`. Do not add H2 — it
  would force dialect-neutral migrations and test a database we don't ship.
- Unit tests (services, authorization rules) use plain JUnit + Mockito with no
  containers. `@Mock` needs `@ExtendWith(MockitoExtension.class)` in Boot 4.
- `TestcontainersConfiguration` is package-private; classes importing it must
  live in the same package.
- No shared integration-test base class yet (two tests, one `@Import` each).
  Extract one in Part 7 if duplication or shared setup makes it worthwhile.

## Parts (progress)

- [X] 
    0. Setup: Initializr projects, repo, CLAUDE.md, docs/, push to GitHub
- [X] 
    1. Infrastructure: compose.yaml (Postgres, RabbitMQ), application.properties per service, apps start and connect
- [ ] 
    2. Domain & persistence: entities, repositories, Flyway, seed data
- [ ] 
    3. Security: SecurityFilterChain, UserDetailsService, PasswordEncoder, @PreAuthorize, ownership
- [ ] 
    4. GraphQL: schema, queries/mutations, onlyFuture filter, error handling
- [ ] 
    5. Messaging: exchange/queue/binding, publish after commit, consumer, retries/DLQ
- [ ] 
    6. Scheduled reminders (extra)
- [ ] 
    7. Tests: unit + Testcontainers integration (GraphQL, RabbitMQ)
- [ ] 
    8. Deliverables: README, Postman collection per role incl. denials
- [ ] 
    9. (Optional) hospitapi-history