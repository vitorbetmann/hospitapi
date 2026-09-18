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
- Spring Framework 7, Spring Security 7, Jakarta EE 11, JSpecify null-safety
  annotations.
- Spring Security 7 accepts only the lambda DSL; `.and()` chaining is gone.
- Actuator classes such as `EndpointRequest` and `HealthEndpoint` moved
  packages in Boot 4's modularization. `SecurityConfig` matches health by
  path string instead.
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

## Domain (Part 2, authoritative)

- User: id, username, passwordHash ({bcrypt}... form), name,
  role (DOCTOR | NURSE | PATIENT), patient (nullable @ManyToOne, PATIENT only)
- Patient: id, name, email, phone
- Appointment: id, patient (@ManyToOne), doctor (@ManyToOne User),
  scheduledAt, status (SCHEDULED | COMPLETED | CANCELLED), reason, notes,
  createdAt, updatedAt
- AppointmentEvent: eventId, type (CREATED | UPDATED), appointmentId,
  patientName, patientEmail, scheduledAt, occurredAt
- Doctors are `User` rows; `Patient` is the only party with contact details (D-026).

## Schema conventions (Part 2)

- Tables are plural snake_case (`users`, never the reserved `user`).
- Keys are `bigint` identity columns (D-024).
- Entities use JPA associations, not raw FK id fields (D-025).
- Enums are stored as `varchar` with a `check` constraint, mapped with
  `@Enumerated(EnumType.STRING)`.
- Timestamps are `timestamptz` ↔ `OffsetDateTime`. Audit columns (`created_at`, `updated_at`) are written by Hibernate,
  not DB defaults.
- Schema and seed data live in separate migrations. Seed rows use explicit IDs (`overriding system value`) followed by
  `alter column id restart with 100`
  (D-027).
- Migration filenames use a double underscore: `V3__description.sql`. A single
  underscore is not a versioned migration.

## Access rules

- DOCTOR and NURSE: create, update, and view appointments.
- Only DOCTOR edits clinical `notes`. How this is enforced (dedicated
  doctor-only mutation vs. rejecting nurse updates that include notes) is
  decided in Part 4 as D-031.
- PATIENT: views only their own appointments.
- All authorization lives in the service layer (D-030); see Security below.
- These rules resolve an ambiguity in the brief and must be documented in the README.

## Security (Part 3)

- HTTP Basic, stateless sessions, CSRF disabled (D-029). `SecurityConfig`
  lives in `config/`.
- URL rules only separate public from authenticated: `/actuator/health` and
  `/actuator/health/**` are `permitAll`; everything else requires
  authentication. `/graphql` is a single URL, so URL rules cannot express the
  access rules.
- Authorization is enforced in service methods (D-030): role checks with
  `@PreAuthorize` (`@EnableMethodSecurity` is on); the patient ownership check
  is programmatic, inside the `@Transactional` service method, throwing
  `AccessDeniedException`. GraphQL controllers carry no security annotations;
  they only call services.
- The principal is the `SecurityUser` record (D-028) in `security/`, carrying
  userId, username, passwordHash, role, and patientId. Its authorities are
  `"ROLE_" + role`; without the prefix every `hasRole(...)` check silently
  denies.
- `DatabaseUserDetailsService` loads users through
  `UserRepository.findByUsername`, which uses `@EntityGraph(attributePaths =
  "patient")` so no lazy proxy is touched inside the security filter.
- Services read the caller with `CurrentUser.get()` (in `security/`).
- Password encoder: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
  Stored hashes must carry the `{bcrypt}` prefix. Rotating a seeded password
  means a new migration with an `update`, never an edit to V2.
- Seeded users: `doctor`/`doctor123`, `nurse`/`nurse123`,
  `patient`/`patient123` (linked to patient 1). Appointment 3 belongs to
  patient 2, which gives a ready-made ownership denial for user `patient`.
- Health: anonymous callers see only `{"status":"UP"}`; any authenticated
  user sees component details (`show-details=when-authorized`, D-020). A
  wrong password returns 401 even on `permitAll` paths, because the Basic
  filter rejects bad credentials before authorization runs.

## Conventions

- Code, identifiers, comments, and commits in English. The README may be in Portuguese.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
- Layering per service: `api` (GraphQL controllers), `service`, `domain`,
  `repository`, `config` (all `@Configuration` classes), `security`
  (principal, user loading, `CurrentUser`), `messaging`.
- Constructor injection only (no field `@Autowired`).
- `@Transactional` comes from `org.springframework.transaction.annotation`,
  never `jakarta.transaction` (which lacks `readOnly`). Read-only service
  methods use `@Transactional(readOnly = true)`.
- `spring.jpa.open-in-view=false`: lazy associations load only inside service
  transactions, never in controllers or the security filter.
- Watch for same-name imports: `AccessDeniedException` comes from
  `org.springframework.security.access` (not `java.nio.file`); `User` in
  application code is the domain entity, not
  `org.springframework.security.core.userdetails.User`.
- Schema changes go through Flyway migrations only (`ddl-auto=validate`).
  Never edit a migration that has already been applied; add a new one.
- Never create scratch or hand-made tables in the `hospitapi` database.
  `baselineOnMigrate` stays `false` (D-021), so Flyway fails at startup on a
  non-empty schema with no history table. Experiment in a throwaway database
  or a Testcontainers instance instead.
- New decisions go in `docs/decisions.md` using its entry template and
  cross-reference conventions, with the next free ID. Never renumber or
  delete entries; supersede them.

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
curl localhost:8080/actuator/health                       # {"status":"UP"} only (anonymous)
curl -u doctor:doctor123 localhost:8080/actuator/health   # details: db + rabbit UP
curl localhost:8081/actuator/health         # rabbit UP (no db, no security by design)
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
- Security integration tests use `@SpringBootTest` with
  `@WithUserDetails("doctor" | "nurse" | "patient")`, which loads the seeded
  users through the real `DatabaseUserDetailsService`. Calls without a user
  fail with `AuthenticationCredentialsNotFoundException`.
- No shared integration-test base class yet (one `@Import` per test class).
  Extract one in Part 7 if duplication or shared setup makes it worthwhile.

## Parts (progress)

- [x] 
    0. Setup: Initializr projects, repo, CLAUDE.md, docs/, push to GitHub
- [x] 
    1. Infrastructure: compose.yaml (Postgres, RabbitMQ), application.properties per service, apps start and connect
- [x] 
    2. Domain & persistence: entities, repositories, Flyway, seed data
- [x] 
    3. Security: SecurityFilterChain, UserDetailsService, PasswordEncoder, @PreAuthorize, ownership
- [ ] 
    4. GraphQL: schema, queries/mutations, onlyFuture filter, error handling. Decide
       notes enforcement (D-031); role-restricted `@PreAuthorize` on create/update;
       map `AppointmentNotFoundException` to GraphQL `NOT_FOUND`
- [ ] 
    5. Messaging: exchange/queue/binding, publish after commit, consumer, retries/DLQ
- [ ] 
    6. Scheduled reminders (extra)
- [ ] 
    7. Tests: unit + Testcontainers integration (GraphQL, RabbitMQ), MockMvc
       filter-chain test (health public, everything else 401)
- [ ] 
    8. Deliverables: Dockerfiles and app services in `compose.yaml` for one-command startup, revisit health-detail
       exposure (D-020; consider `management.endpoint.health.roles=DOCTOR,NURSE`), README (architecture, how to run,
       schema, example operations, credentials, access rules, stateless/CSRF-off design), Postman collection per role
       incl. denials
- [ ] 
    9. (Optional) Extract the `history/` service