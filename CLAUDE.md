# CLAUDE.md – hospitapi

Hospital scheduling backend for the POSTECH Java Tech Challenge, Phase 3.
This file and `docs/decisions.md` are the source of truth. If a request
conflicts with them, stop and ask instead of silently changing course.

## Architecture

Monorepo, one standalone Maven project per service (no root pom), shared
root `compose.yaml`.

- `scheduling/` (artifact `hospitapi-scheduling`): Spring Security (HTTP Basic,
  DB-backed users), Spring for GraphQL, JPA + PostgreSQL, Flyway. Publishes
  appointment events to RabbitMQ after the transaction commits (D-036). Runs
  the 24-hour reminder job, which publishes REMINDER_DUE events (Part 6,
  D-040).
- `notification/` (artifact `hospitapi-notification`): consumes appointment
  events, including REMINDER_DUE, and logs/mocks reminders. Stateless (D-038):
  no database, no Spring Security, no scheduled job; Spring Web only to expose
  `/actuator/health`.
- `history/` (artifact `hospitapi-history`): optional stretch goal. Do not
  start it until all mandatory parts work.
- Broker: RabbitMQ, with JSON messages.

## Stack

- Java 21, **Spring Boot 4.1.1**, Maven (each service's own wrapper), Jar packaging
- Group `com.vitorbetmann`. Packages `com.vitorbetmann.hospitapi.scheduling`
  and `com.vitorbetmann.hospitapi.notification`
- `application.properties` only (no YAML)
- scheduling: PostgreSQL, Flyway, Spring Data JPA, Spring Security, Spring for
  GraphQL, Spring AMQP, Actuator, Lombok, Testcontainers
- notification: Spring AMQP, Spring Web MVC (health only), Actuator, Lombok, Testcontainers (RabbitMQ only)
- Infrastructure (Postgres + RabbitMQ) is started with `docker compose up -d`
  from the repo root. `compose.yaml` contains infrastructure only during
  development (D-018); services run on the host with `./mvnw spring-boot:run`
  — scheduling on 8080, notification on 8081. The `spring-boot-docker-compose`
  dependency is intentionally not used.

### Spring Boot 4 notes (don't write 3.x-style code by habit)

- Jackson 3 is the default: packages are `tools.jackson.*`, not
  `com.fasterxml.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`).
  Don't use Jackson 2-specific classes such as `Jackson2JsonMessageConverter`;
  the AMQP converter is `JacksonJsonMessageConverter` (see Messaging).
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
- GraphQL test annotations moved to
  `org.springframework.boot.graphql.test.autoconfigure.tester`
  (e.g. `AutoConfigureGraphQlTester`). The HTTP GraphQL tester is built on
  `WebTestClient`, so it needs WebFlux on the test classpath.
- Spring Framework 7, Spring Security 7, Jakarta EE 11, JSpecify null-safety
  annotations.
- Spring Security 7 accepts only the lambda DSL; `.and()` chaining is gone.
- Actuator classes such as `EndpointRequest` and `HealthEndpoint` moved
  packages in Boot 4's modularization. `SecurityConfig` matches health by
  path string instead.
- Listener retry counts **retries**, not attempts: use
  `spring.rabbitmq.listener.simple.retry.max-retries` (retries after the
  first attempt, default 3). `max-attempts` is deprecated at level `error`
  in Boot 4 and silently ignored — setting it leaves the default in place.
- To check whether a property still exists, read
  `META-INF/spring-configuration-metadata.json` inside the relevant Boot jar
  in `~/.m2` (e.g. `spring-boot-amqp-4.1.1.jar`); it lists deprecations and
  replacements. The IDE's properties autocomplete reads the same file.
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
- **graphql-java 25.0** is managed by Boot. `graphql-java-extended-scalars`
  is pinned at 24.0 (no 25.x release yet, D-032). `dependency:tree` shows
  graphql-java under extended-scalars only because Maven prints each artifact
  once, at the first path it reaches.
- **RabbitMQ 4.3+** rejects non-durable, non-exclusive queues (`transient_nonexcl_queues` is denied by default). The
  `rabbitmq:4-management-alpine` tag floats across 4.x, which is how 4.3
  arrived without any change in this repo.
- **Hibernate 7** (managed by Boot 4): the `jakarta.persistence.lock.timeout`
  hint value `-2` is meant to produce `SKIP LOCKED`. Verify it in the
  generated SQL (see Scheduled reminders) rather than trusting it.

## Domain (Part 2, authoritative)

- User: id, username, passwordHash ({bcrypt}... form), name,
  role (DOCTOR | NURSE | PATIENT), patient (nullable @ManyToOne, PATIENT only)
- Patient: id, name, email, phone
- Appointment: id, patient (@ManyToOne), doctor (@ManyToOne User),
  scheduledAt, status (SCHEDULED | COMPLETED | CANCELLED), reason, notes,
  reminderSentAt (nullable, D-040), createdAt, updatedAt
    - `scheduledAt` and `reminderSentAt` have no setters (`@Setter(AccessLevel.NONE)` over the class-level `@Setter`).
      `scheduledAt` changes only through `reschedule(OffsetDateTime)`, which
      clears `reminderSentAt` when the instant actually changes (compared
      with `isEqual`, not `equals`, so a different offset for the same moment
      is not a change). `reminderSentAt` is set only through
      `markReminderSent(OffsetDateTime)`. `create` and `update` both call
      `reschedule`.
- AppointmentEvent (record, `messaging/`): eventId,
  type (CREATED | UPDATED | REMINDER_DUE), appointmentId, status,
  patientName, patientEmail, scheduledAt, occurredAt.
  `status` lets consumers tell a cancellation or completion from a reschedule.
  Each service owns its own copy of the record and enums; the JSON field
  names are the contract (D-037).
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
- Seed appointment dates are relative to migration time (`now() ± interval`),
  not fixed dates. Tests account for this (see Testing).
- Migration filenames use a double underscore: `V3__description.sql`. A single
  underscore is not a versioned migration.

## Access rules

- DOCTOR and NURSE: create, update, and view appointments.
- Only DOCTOR edits clinical `notes`, through the dedicated
  `updateClinicalNotes` mutation (D-031). `notes` is not part of the create or
  update inputs.
- PATIENT: views only their own appointments (including their `notes`).
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
- Seeded users: `doctor`/`doctor123` (user id 1), `nurse`/`nurse123`
  (user id 2), `patient`/`patient123` (user id 3, linked to patient 1).
  Appointments 1 and 2 belong to patient 1; appointment 3 belongs to
  patient 2, which gives a ready-made ownership denial for user `patient`.
- Health: anonymous callers see only `{"status":"UP"}`; any authenticated
  user sees component details (`show-details=when-authorized`, D-020). A
  wrong password returns 401 even on `permitAll` paths, because the Basic
  filter rejects bad credentials before authorization runs.
- The reminder job runs with no authenticated user, so `ReminderService`
  carries no `@PreAuthorize` and is never called from GraphQL.

## GraphQL (Part 4)

- The schema lives at `src/main/resources/graphql/*.graphqls`. The `.graphqls`
  extension is required; with any other extension Boot silently skips GraphQL.
- After schema or controller changes, check the startup "GraphQL schema
  inspection" report for unmapped fields or controller methods.
- Operations: queries `appointment(id)` and
  `appointmentsByPatient(patientId, onlyFuture = false)`; mutations
  `createAppointment`, `updateAppointment(id, input)`,
  `updateClinicalNotes(id, notes)`. No queries beyond the brief (no
  `patients`, `doctors`, `myAppointments`). `reminderSentAt` is internal and
  not exposed in the schema.
- Root fields are nullable, so an error on one field doesn't wipe out `data`.
- `scalar DateTime` comes from `ExtendedScalars.DateTime`, registered with a
  `RuntimeWiringConfigurer` in `config/GraphQlConfig` (D-032). A round-trip
  test guards compatibility with graphql-java 25.0.
- `DateTime` output offsets vary: a value reloaded from Postgres comes back in
  UTC (`...Z`), while a value just written echoes the client's offset. Same
  instant; clients must not compare them as strings.
- The `Doctor` GraphQL type (`id`, `name`) is backed by the `User` entity and
  resolves through getters, with no `@SchemaMapping`.
- Repository methods returning appointments to GraphQL declare
  `@EntityGraph(attributePaths = {"patient", "doctor"})` (D-033). Open-in-view
  is off, so any association a query selects must already be loaded.
- `updateAppointment`: absent and `null` fields both mean "unchanged" (fields
  can't be cleared), and only `SCHEDULED` appointments can be updated.
  `updateClinicalNotes` works in any status. `create` calls `save()` (IDENTITY
  keys insert immediately, so the id is available for the event); updates
  rely on dirty checking, with no `save()`.
- Error classifications (Spring GraphQL's `ErrorType`), mapped in
  `api/GraphQlExceptionResolver`:
    - `AppointmentNotFoundException` → `NOT_FOUND`
    - `InvalidAppointmentException` → `BAD_REQUEST`
    - `AccessDeniedException` falls through to Spring GraphQL's security
      resolver → `FORBIDDEN` (`UNAUTHORIZED` when there is no authenticated user)

## Messaging (Part 5)

### Topology

- Exchange: durable topic exchange `hospitapi.appointments`. Routing keys
  `appointment.created`, `appointment.updated` and `appointment.reminder-due`
  live on scheduling's `AppointmentEventType.routingKey()`.
- Routing keys have exactly two dot-separated words. `*` in a binding matches
  exactly one word, so notification's `appointment.*` matches
  `appointment.reminder-due` (the hyphen doesn't split words) but would not
  match `appointment.reminder.due`. A topic exchange silently drops messages
  that match no binding: no error, no DLQ.
- Scheduling declares the exchange in its `config/MessagingConfig`
  (`MessagingConfig.APPOINTMENTS_EXCHANGE`, public so `messaging/` can read
  it). Notification declares the same exchange again, with identical
  properties, so either service can start first (D-039). The producer never
  knows its consumers.
- Each consumer owns its queue, bindings, DLX and DLQ. Notification's, in its
  `config/MessagingConfig`:
    - queue `notification.appointment-events` (durable), bound with
      `appointment.*`, with `x-dead-letter-exchange=notification.dlx` and
      `x-dead-letter-routing-key=notification.appointment-events.dlq`
    - direct exchange `notification.dlx` → queue
      `notification.appointment-events.dlq`
- Declare every queue durable, including test queues (RabbitMQ 4.3+, see
  Version notes).
- Queue arguments are fixed at first declaration. After changing DLX or other
  queue arguments, delete the queue in the management UI, or run
  `docker compose down -v` (which also wipes pgdata; Flyway reseeds).
- Message converter (both services): `JacksonJsonMessageConverter` built from
  Boot's `JsonMapper` (`tools.jackson.databind.json.JsonMapper`), declared in
  `MessagingConfig`. Boot applies it to `RabbitTemplate` and listener
  containers only if the bean type is
  `org.springframework.amqp.support.converter.MessageConverter`.
  `org.springframework.messaging.converter` has same-name classes (`MessageConverter`, `JacksonJsonMessageConverter`)
  that compile but are
  silently ignored; the symptom is `SimpleMessageConverter only supports
  String, byte[] and Serializable payloads`.

### Producer (scheduling)

- Publishing after commit (D-036): `AppointmentService` builds
  `AppointmentEvent.of(type, appointment, clock)` inside the `@Transactional`
  method and publishes it with `ApplicationEventPublisher`.
  `AppointmentEventPublisher` (`@TransactionalEventListener(phase = AFTER_COMMIT)`)
  is the only class that touches `RabbitTemplate`.
- The listener runs after the persistence context has closed, so it must never
  touch entity associations. Everything a consumer needs goes into the event.
- The listener never throws: it catches `RuntimeException` and logs event type,
  eventId and appointmentId. A failed send after commit loses the event; this
  is the accepted trade-off in D-036.
- `create` publishes CREATED, `update` publishes UPDATED (also when the status
  changes to COMPLETED or CANCELLED). Rejected updates publish nothing.
  `updateClinicalNotes` publishes nothing, and the event never carries `notes`.
  The reminder job publishes REMINDER_DUE through the same after-commit path (D-040; see Scheduled reminders).
- Spring swallows exceptions from after-commit listeners and logs only
  `TransactionSynchronization.afterCompletion threw exception`, followed by
  the stack trace. When an event doesn't arrive, read the log, not the assertion.

### Consumer (notification)

- Contract (D-037): notification has its own `AppointmentEvent` record plus
  `AppointmentEventType` and `AppointmentStatus` enums in `messaging/`. Never
  share Java types between services. The listener binds by its parameter type (the converter's default INFERRED
  precedence), ignoring scheduling's
  `__TypeId__` header. Unknown JSON fields are tolerated; an unknown enum
  value fails and ends up in the DLQ.
- New enum constants go on the consumer side first. When running locally,
  restart notification before scheduling publishes a new event type, or
  those events land in the DLQ.
- `AppointmentEventListener` (`@RabbitListener`) sends a reminder only when
  `status == SCHEDULED`, for every event type including REMINDER_DUE. Any
  other status is acknowledged and logged at DEBUG. Never throw for a valid
  message: throwing triggers retries and dead-letters it.
- Reminders go through the `ReminderSender` interface (`reminder/`); the only
  implementation, `LoggingReminderSender.java`, logs them. Times are logged in UTC.
- Retries: `spring.rabbitmq.listener.simple.retry.max-retries=2` (3 attempts
  total), 1s initial interval, ×2 multiplier, 10s max. After the last attempt
  Boot's default `RejectAndDontRequeueRecoverer` rejects the message and the
  queue's DLX routes it to the DLQ. No custom recoverer.
- Stateless (D-038): no database and no dedupe by `eventId`. At-least-once
  delivery can produce a duplicate reminder; that is accepted.
- Management API and UI queue counts lag by up to ~5s. Re-check before
  concluding a message is missing.

## Scheduled reminders (Part 6, D-040)

- The job lives in scheduling, which owns the appointment data. Notification
  stays stateless and treats REMINDER_DUE like any other event. D-040
  corrects D-038's consequence note; D-038's decision is unchanged.
- Due means: `status = SCHEDULED`, `scheduledAt` in `[now, now + window)`,
  `reminderSentAt is null`. `now` comes from the injected `Clock`.
- `service/ReminderService.publishDueReminders()` is `@Transactional`: it
  reads due rows, calls `markReminderSent(now)` on each, and publishes
  `AppointmentEvent.of(REMINDER_DUE, appointment, clock)` with
  `ApplicationEventPublisher`. Dirty checking saves the marks; no `save()`.
- `service/ReminderJob` is a thin `@Scheduled` bean that only calls
  `ReminderService`. They are separate beans because `@Transactional` works
  only through the proxy, and so tests can call the service directly.
- `config/SchedulingConfig` carries `@EnableScheduling`, guarded by
  `@ConditionalOnBooleanProperty(name = "hospitapi.reminders.enabled", matchIfMissing = true)`.
- Properties: `hospitapi.reminders.enabled` (default true),
  `hospitapi.reminders.interval` (ISO-8601 duration, default `PT5M`, used as
  `fixedDelayString`), `hospitapi.reminders.window` (default `PT24H`).
- The repository query uses `@Lock(PESSIMISTIC_WRITE)` plus the
  `jakarta.persistence.lock.timeout = -2` hint, so overlapping runs or
  instances skip rows another transaction has claimed. Verify once with
  `logging.level.org.hibernate.SQL=DEBUG` that the SQL ends in `skip locked`.
  Don't `join fetch` the patient in this query: `FOR UPDATE` over a join
  would lock patient rows too. The lazy patient loads inside the transaction.
- Accepted trade-offs (D-040): the D-036 loss window applies (if the broker
  is unreachable right after commit, `reminderSentAt` is set and that
  reminder is lost). An appointment created less than one window ahead gets
  both its CREATED reminder and a REMINDER_DUE reminder. Both go in the
  README.
- Never let the job run on a timer in tests (see Testing).

## Conventions

- Code, identifiers, comments, and commits in English. The README may be in Portuguese.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
- Layering per service: `api` (GraphQL controllers and the GraphQL exception
  resolver), `service` (services, GraphQL input records, service exceptions,
  the reminder job), `domain`, `repository`, `config` (all `@Configuration`
  classes), `security` (principal, user loading, `CurrentUser`), `messaging`
  (events, event types, publishers and listeners). Notification adds
  `reminder` (the `ReminderSender` port and its implementations).
- GraphQL controllers are one-line delegations to services.
- Constructor injection only (no field `@Autowired`).
- Code that needs "now" uses the injected `Clock` bean (`config/ClockConfig`,
  `Clock.systemUTC()`), never `Instant.now()` or `OffsetDateTime.now()`.
- `@Transactional` comes from `org.springframework.transaction.annotation`,
  never `jakarta.transaction` (which lacks `readOnly`). Read-only service
  methods use `@Transactional(readOnly = true)`.
- `spring.jpa.open-in-view=false`: lazy associations load only inside service
  transactions, never in controllers or the security filter.
- Watch for same-name imports: `AccessDeniedException` comes from
  `org.springframework.security.access` (not `java.nio.file`); `User` in
  application code is the domain entity, not
  `org.springframework.security.core.userdetails.User`; AMQP converter types
  come from `org.springframework.amqp.support.converter`, not
  `org.springframework.messaging.converter`.
- Compare `OffsetDateTime` values by instant (`isEqual`, `isAtSameInstantAs`
  in AssertJ), never with `equals`: values reloaded from Postgres are UTC,
  client values carry their own offset.
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
  not live in a cloud-synced folder (OneDrive, iCloud Desktop/Documents);
  OneDrive locks files in `target/` and breaks `mvnw clean`. On Windows the
  repo lives at `C:\_dev\hospitapi`.
- Docker Desktop on Windows needs CPU virtualization enabled in the firmware (Dell: F2 → Virtualization → Intel
  Virtualization Technology → Apply
  Changes) and WSL 2 (`wsl --install --no-distribution`, then reboot). If
  `docker info` shows a Client section but the Server section errors, the
  engine VM isn't running.
- Maven wrapper: `./mvnw` on macOS, Git Bash, Linux, and in containers;
  `mvnw.cmd` (or `.\mvnw` in PowerShell) on Windows. Stopping
  `spring-boot:run` on Windows may end at `Terminate batch job (Y/N)?`;
  answer `Y`, or the app keeps running.
- Maven commands run from inside a service folder; the repo root has no pom (D-001). In zsh, quote `-D` arguments
  containing `*`
  (`-Dincludes='org.mockito:*'`), or zsh expands the glob and fails.
- Create Java sources with the IDE's "New Java Class". A file without the
  `.java` extension is silently skipped by Maven; the symptom is a green
  build with fewer compiled files or tests than expected (`Compiling N source files`, `Tests run: N`).
- Snippets pasted from chat may omit imports. A `cannot find symbol` for
  `Configuration`, `Bean` or `Clock` means a missing import, not a missing
  dependency.
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
docker compose down -v                      # WIPES pgdata and queues — Flyway re-runs from V1
cd scheduling && ./mvnw spring-boot:run     # :8080  (Windows: mvnw.cmd spring-boot:run)
cd notification && ./mvnw spring-boot:run   # :8081
curl localhost:8080/actuator/health                       # {"status":"UP"} only (anonymous)
curl -u doctor:doctor123 localhost:8080/actuator/health   # details: db + rabbit UP
curl localhost:8081/actuator/health         # rabbit UP (no db, no security by design)
./mvnw test                                 # inside a service folder; Docker, no compose
./mvnw clean test                           # after moving/renaming test resources (drops stale copies in target/)
./mvnw spring-boot:test-run                 # run against throwaway containers

# notification with DEBUG logging (shows skipped non-SCHEDULED events)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--logging.level.com.vitorbetmann.hospitapi.notification=DEBUG"

# scheduling with a short reminder interval and SQL logging (manual Part 6 check)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--hospitapi.reminders.interval=PT30S --logging.level.org.hibernate.SQL=DEBUG"

# queue depth and consumers via the management API
curl -u hospitapi:hospitapi localhost:15672/api/queues/%2F/notification.appointment-events
curl -u hospitapi:hospitapi localhost:15672/api/queues/%2F/notification.appointment-events.dlq
```

RabbitMQ management UI: `localhost:15672` (`hospitapi` / `hospitapi`).

PowerShell GraphQL calls: use `Invoke-RestMethod` with GraphQL variables,
put the query in single quotes (double quotes make PowerShell expand `$id`),
and pass `-Depth 5` to `ConvertTo-Json` (the default depth of 2 mangles
nested inputs).

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
- `TestcontainersConfiguration` is package-private in both services, so tests
  importing it live in the root test package of their service.

### Integration tests: one shared context (D-034)

- Every integration test class is annotated `@IntegrationTest`, a
  meta-annotation bundling `@SpringBootTest`, `@AutoConfigureGraphQlTester`
  and `@Import({TestcontainersConfiguration.class, TestClockConfiguration.class,
  TestMessagingConfiguration.class})`.
  Never use a bare `@SpringBootTest` + `@Import`: all classes must have the
  identical configuration to share one cached context, and with it one pair
  of containers per test run.
- Per-class `@MockitoBean`, `@MockitoSpyBean`, `@ActiveProfiles`,
  `@TestPropertySource`, `@AutoConfigure...` or `@DirtiesContext` change the
  context cache key and start a new context with new containers. Avoid them in
  integration tests; if something is needed everywhere, add it to
  `@IntegrationTest`.
- `@IntegrationTest` disables the reminder timer with
  `@SpringBootTest(properties = "hospitapi.reminders.enabled=false")`, so
  every class shares that setting. Don't add
  `src/test/resources/application.properties` for it: it would shadow the
  main `application.properties` entirely (same classpath name).
- To check sharing: the Spring Boot banner appears once per `./mvnw test`
  run. For cache statistics, temporarily set
  `logging.level.org.springframework.test.context.cache=DEBUG`.
- `@IntegrationTest`, `TestcontainersConfiguration`, `TestClockConfiguration`
  and `TestMessagingConfiguration` are package-private, so integration tests
  live in the root test package `com.vitorbetmann.hospitapi.scheduling`.

### Integration tests: data and time (D-035)

- No `@Transactional` on integration tests. A test transaction keeps entities
  managed during assertions and hides lazy-loading bugs (D-033). It would
  also roll back instead of committing, so no after-commit event would ever
  be sent.
- Seeded rows are read-only in tests. Data created by tests belongs to test
  patient 900, inserted by `src/test/resources/sql/test-patient.sql` via
  `@Sql(scripts = "/sql/test-patient.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)`.
  The script is idempotent (`on conflict do nothing`).
- Test fixtures live in `src/test/resources/sql/`. `@Sql` paths are relative
  to the classpath root, so `/sql/x.sql` must be at
  `src/test/resources/sql/x.sql`.
- `TestClockConfiguration` provides a `@Primary` bean named `testClock`, fixed
  at test start + 1 day. Seed dates are relative to migration time, so in
  tests appointment 3 (+20h) is in the past and appointment 1 (+3d) in the
  future; this proves services use the injected `Clock`. The bean name must
  differ from the application's `clock` bean (bean overriding is off).

### Security and GraphQL tests

- `@WithUserDetails("doctor" | "nurse" | "patient")` loads the seeded users
  through the real `DatabaseUserDetailsService`. Service calls without a user
  throw `AuthenticationCredentialsNotFoundException`; through GraphQL they
  surface as `UNAUTHORIZED`.
- GraphQL tests use `ExecutionGraphQlServiceTester` (in-process, no HTTP; the
  filter chain is covered by the Part 7 MockMvc test).
- Assert `.errors()` before `.path(...)`: `GraphQlTester` fails a test whose
  response has errors that were never checked. Error tests also check that
  the root field is `null`.
- Compare `DateTime` values by instant (`isAtSameInstantAs`), never as
  strings: Postgres stores UTC, so a reloaded value's offset may differ.
- Write `DateTime` test values as string literals with explicit seconds (`2030-01-15T10:30:00-03:00`);
  `OffsetDateTime.toString()` drops `:00`
  seconds, which RFC 3339 requires.
- To decode lists that may be empty, map the root field to a small record (`entityList(IdOnly.class)`) instead of a
  `[*]` JSON path.

### Messaging tests, producer (scheduling)

- `TestMessagingConfiguration` declares the durable queue
  `test.appointment-events` (`TEST_QUEUE`), bound to the real
  `hospitapi.appointments` exchange with `appointment.#`. Because the context
  is shared, events from every integration test class land in it.
- Purge it in `@BeforeEach` with `amqpAdmin.purgeQueue(TEST_QUEUE, false)`,
  and again after setup steps that publish (e.g. the `create` before an
  `update` test), so only the event under test is received.
- Receive with `rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000,
  new ParameterizedTypeReference<AppointmentEvent>() {})`. Short timeouts (500–1000 ms) with `receive(...)` returning
  `null` prove absence.
- Prove after-commit with `TransactionTemplate`: call the service inside
  `executeWithoutResult` (its `@Transactional` joins the outer transaction),
  assert the queue is still empty, then assert the event after the template
  commits. `tx.setRollbackOnly()` proves a rollback publishes nothing.
- "Nothing arrived" tests only mean something while the positive tests pass.
- Compare event timestamps by instant: `event.occurredAt().toInstant()`
  against `clock.instant()`.

### Reminder job tests (scheduling)

- `@IntegrationTest` class that calls `ReminderService.publishDueReminders()`
  directly; the timer is off in tests.
- Create appointments for test patient 900 relative to `clock.instant()`
  (the shifted test clock), never relying on seed positions. Purge
  `TEST_QUEUE` after setup, since `create` publishes CREATED.
- Cases: +2h SCHEDULED is claimed (one REMINDER_DUE, `reminderSentAt` set);
  +30h and a +2h CANCELLED one are skipped; a second run publishes nothing;
  rescheduling to a different instant makes it claimable again; an update to
  the same instant with a different offset does not.
- Earlier tests in the shared context may leave due appointments for
  patient 900. Assert on the appointment ids a test created, not on the
  total number of events.

### Messaging tests, consumer (notification)

- `AppointmentEventListenerTest` (root test package) uses `@SpringBootTest`
  with `@Import(TestcontainersConfiguration.class)`, a `@MockitoBean
  ReminderSender`, and properties that shorten the retry intervals (100ms initial, 200ms max).
- Publish **raw JSON** with `rabbitTemplate.send(...)` and scheduling's real
  `__TypeId__` header (`com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEvent`),
  never `convertAndSend` with notification's own record. This is what proves
  the contract is the JSON, not the Java class (D-037).
- Every event type needs a binding test, REMINDER_DUE included (routing key
  `appointment.reminder-due`): it catches a missing enum constant, which
  would otherwise surface only as a message in the DLQ.
- Wait for asynchronous delivery with Mockito: `verify(mock, timeout(5_000))`
  for arrival, `verify(mock, after(1_000).never())` for absence.
- Purge the DLQ in `@BeforeEach`; read it with
  `rabbitTemplate.receive(DEAD_LETTER_QUEUE, 10_000)`.
- The retry test asserts exactly `times(3)` calls. It guards
  `max-retries=2`, and it is how the Boot 4 `max-attempts` rename was caught.

## Parts (progress)

- [x] 
    0. Setup: Initializr projects, repo, CLAUDE.md, docs/, push to GitHub
- [x] 
    1. Infrastructure: compose.yaml (Postgres, RabbitMQ), application.properties per service, apps start and connect
- [x] 
    2. Domain & persistence: entities, repositories, Flyway, seed data
- [x] 
    3. Security: SecurityFilterChain, UserDetailsService, PasswordEncoder, @PreAuthorize, ownership
- [x] 
    4. GraphQL: schema, queries/mutations, onlyFuture filter, error handling, notes via
       `updateClinicalNotes` (D-031), GraphQL integration tests (D-034, D-035)
- [x] 
    5. Messaging: exchange and publish after commit in scheduling (D-036);
       notification consumer with queue, DLX/DLQ, retries, status filter (D-037, D-038, D-039); producer and consumer
       tests; manual end-to-end
       check (reminders, skip on cancel, durability while notification is
       down, poison message to DLQ)
- [ ] 
    6. Scheduled reminders (extra), D-040. Done: `Clock` bean moved to
       `config/ClockConfig`; `reminder_sent_at` migration; entity
       `reschedule` / `markReminderSent` with setters removed; REMINDER_DUE in
       both enums. Remaining: claim query, `ReminderService`, `ReminderJob`,
       `SchedulingConfig` + properties, disabling the timer in
       `@IntegrationTest`, reminder job tests, REMINDER_DUE consumer test,
       manual end-to-end check.
- [ ] 
    7. Tests: unit tests, Testcontainers RabbitMQ integration, MockMvc
       filter-chain test (health public, everything else 401). GraphQL
       integration tests were done in Part 4.
- [ ] 
    8. Deliverables: Dockerfiles and app services in `compose.yaml` for one-command startup, revisit health-detail
       exposure (D-020; consider `management.endpoint.health.roles=DOCTOR,NURSE`), README (architecture, how to run,
       schema, example operations, credentials, access rules, stateless/CSRF-off design, D-036 event-loss
       trade-off, async durability demo, UTC timestamps and varying offsets, Windows prerequisites for Docker
       Desktop, reminder job behaviour and its D-040 trade-offs), Postman collection per role incl. denials (e.g.
       PATIENT calling `createAppointment` → `FORBIDDEN`, no event published). Align local JDK 25.0.1 with the
       Dockerfile's Java 21; consider pinning the RabbitMQ minor tag (new decision).
- [ ] 
    9. (Optional) Extract the `history/` service