# Decisions log

Each entry records a choice between real alternatives that shapes the project.
Setup steps and how-tos belong in CLAUDE.md or the README, not here.

Rules:

- IDs never change. To change a decision, add a new entry marked
  `Supersedes D-xxx` and set the old entry's status to `Superseded by D-yyy`.
- Keep each entry short: what was decided, and why.

Cross-references — two mechanisms, deliberately distinct:

- **`Supersedes D-xxx` / `Superseded by D-xxx`** — a later decision reverses
  or replaces an earlier one. New entry, new ID; the old entry's text is
  never edited, only its Status changes to `Superseded by D-yyy`.
- **`[... — see D-xxx]`** — an editorial annotation on an entry whose
  decision still stands but whose details have drifted (a renamed file, a
  moved path). Added in square brackets so the original wording stays
  legible. No new ID.

If unsure which applies: ask whether the original choice would be made
differently today. If yes, supersede. If the choice is unchanged and only its
description is stale, annotate.

Entry template:

```
### D-000 Title
- Status: Accepted | Superseded by D-xxx
- Part: Planning | 0 | 1 | ...
- Decision: ...
- Why: ...
- Alternatives considered: ...
```

---

### D-001 Monorepo with one standalone Maven project per service

- Status: Accepted
- Part: Planning
- Decision: A single repo `hospitapi` with one folder per service, each its
  own Maven project with its own `mvnw`, no root pom. One root `docker-compose.yml`
  [now `compose.yaml` — see D-019].
- Why: The brief asks for separate services; standalone projects keep them
  independently buildable, while one repo keeps delivery and review simple.
- Alternatives considered: multi-module Maven build; one repo per service.

### D-002 RabbitMQ as the message broker

- Status: Accepted
- Part: Planning
- Decision: Use RabbitMQ with JSON messages for scheduling → notification events.
- Why: Simple to run locally and a natural fit for point-to-point events.
- Alternatives considered: Kafka (more operational weight than this scope needs).

### D-003 HTTP Basic authentication with DB-backed users

- Status: Accepted
- Part: Planning
- Decision: Spring Security HTTP Basic; users and roles (DOCTOR, NURSE,
  PATIENT) stored in PostgreSQL.
- Why: The brief asks for basic authentication; DB-backed users make roles
  and patient ownership realistic.
- Alternatives considered: in-memory users; JWT/OAuth2 (beyond scope).

### D-004 Access rules

- Status: Accepted
- Part: Planning
- Decision: DOCTOR and NURSE create, update, and view appointments; only
  DOCTOR edits clinical notes; PATIENT views only their own appointments,
  enforced by an ownership check in the service layer.
- Why: Resolves an ambiguity in the brief; documented in the README.

### D-005 Naming

- Status: Accepted
- Part: Planning, revised in Part 0
- Decision: Repo `hospitapi`; group `com.vitorbetmann`; service folders
  `scheduling/`, `notification/`, `history/`; Maven artifactIds keep the
  prefix (`hospitapi-scheduling`, `hospitapi-notification`, `hospitapi-history`).
- Why: The repo name already gives folders context, but jars and images
  leave the repo and need unique names.

### D-006 Claude Code implements; repo docs are the source of truth

- Status: Accepted
- Part: Planning
- Decision: Implementation happens in Claude Code; `CLAUDE.md` and this file
  are authoritative. Chats are for planning, learning, and review.

### D-007 Spring Boot 4.1.1

- Status: Accepted
- Part: 0
- Decision: Use Spring Boot 4.1.1 (planning originally said 3.x).
- Why: All 3.x lines lost OSS support in June 2026; Initializr generates 4.x
  cleanly, and a greenfield project avoids most migration pain.
- Alternatives considered: Spring Boot 3.5.16 (closer to course material, but unsupported).

### D-008 YAML configuration

- Status: Superseded by D-015
- Part: 0
- Decision: `application.yml` instead of `application.properties`.
- Why: Nested config (datasource, RabbitMQ, JPA, GraphQL) is easier to read
  as a tree; profiles fit in one file.
- Alternatives considered: `.properties` (functionally equivalent).

### D-009 Flyway owns the database schema

- Status: Accepted
- Part: 0
- Decision: All schema changes and seed data go through Flyway SQL
  migrations; Hibernate runs with `ddl-auto: validate`.
- Why: Versioned, reproducible schema and seed users for reviewers;
  Hibernate fails fast if entities and schema diverge.
- Alternatives considered: `ddl-auto: update` (no history, no seed data,
  schema drift); Liquibase.

### D-010 No spring-boot-docker-compose dependency

- Status: Accepted
- Part: 0
- Decision: Infrastructure starts with `docker compose up` from the repo root.
- Why: The dependency expects a compose file per service and auto-starts
  containers, which conflicts with a shared root compose file.

### D-011 Actuator in both services

- Status: Accepted
- Part: 0
- Decision: Add Actuator to both; notification includes Spring Web only to
  expose `/actuator/health`.
- Why: Easy startup verification and Docker Compose healthchecks.

### D-012 Projects generated by hand at start.spring.io

- Status: Superseded by D-016
- Part: 0
- Decision: Generate each service through the Initializr web UI with the
  package name set explicitly; record each "Share" link in the README.
- Why: Learning goal: know exactly which dependencies each service uses.
- Alternatives considered: generating via the Initializr API with curl.

### D-013 Single root .gitignore

- Status: Accepted
- Part: 0
- Decision: One `.gitignore` at the repo root; per-service generated files removed.
- Why: One place to maintain; patterns apply at any depth.

### D-014 LF line endings everywhere, enforced by .gitattributes

- Status: Accepted
- Part: 0
- Decision: `.gitattributes` forces LF for all text files, CRLF only for
  `*.cmd`/`*.bat`; `mvnw` is committed as executable.
- Why: Development alternates between macOS and Windows, and the services
  run in Linux containers; one consistent line ending avoids broken scripts
  and noisy diffs.
- Alternatives considered: native line endings per OS (`* text=auto`).

### D-015 Properties configuration

- Status: Accepted
- Part: 0
- Decision: Use `application.properties`; no YAML. Supersedes D-008. (Corrected in Part 1: Initializr generated
  `application.yaml`; the switch
  to `.properties` was a deliberate rename, not the generated default.)
- Why: Personal preference; flat keys are explicit and avoid YAML's
  indentation and value-parsing pitfalls.
- Alternatives considered: `application.yml`.

### D-016 Projects generated by hand at start.spring.io, no share links

- Status: Accepted
- Part: 0
- Decision: Each service was generated through the Initializr web UI with the
  package name set explicitly. Share links are not recorded; the committed
  `pom.xml` files are the record of dependencies. Supersedes D-012.
- Alternatives considered: recording share links in the README; generating via curl.

### D-017 Postgres 18 volume mounted at /var/lib/postgresql

- Status: Accepted
- Part: 1
- Decision: `compose.yaml` pins `postgres:18-alpine` and mounts the named
  volume `pgdata` at `/var/lib/postgresql`, not `/var/lib/postgresql/data`.
- Why: The official image made `PGDATA` version-specific in 18+ (`/var/lib/postgresql/18/docker`) and moved the declared
  `VOLUME`. Mounting
  the pre-18 path fails silently: Docker creates an anonymous volume, the data
  lands there, and `pgdata` stays empty. Verified with a `compose down` / `up`
  persistence round trip.
- Alternatives considered: pinning `postgres:17-alpine` to keep the familiar
  path; overriding `PGDATA` back to the old location.

### D-018 compose.yaml holds infrastructure only during development

- Status: Accepted
- Part: 1
- Decision: During development `compose.yaml` runs Postgres and RabbitMQ only.
  Services run on the host with `./mvnw spring-boot:run`. Dockerfiles and app
  services are added in Part 8, where one-command startup for evaluators is a
  deliverable.
- Why: Faster feedback loop while developing; no image rebuild per code change.
- Alternatives considered: containerizing both services from the start.

### D-019 The Compose file is named compose.yaml

- Status: Accepted
- Part: 1
- Decision: The root file is `compose.yaml`, the name Docker Compose now
  prefers. This is the name the README must use. Corrects the
  `docker-compose.yml` filename referenced in D-001 and D-010; those
  decisions otherwise stand.
- Alternatives considered: keeping `docker-compose.yml`.

### D-020 Actuator health detail exposure differs per service

- Status: Accepted
- Part: 1
- Decision: `management.endpoint.health.show-details=when-authorized` in
  scheduling, `always` in notification.
- Why: Scheduling has Spring Security, so `when-authorized` works as intended.
  Notification has no Security dependency, so `when-authorized` would hide
  details from everyone and defeat the connectivity check it exists for.
- Alternatives considered: `always` in both (leaks db/broker details from a
  secured service); adding Security to notification just for this.
- Revisit: Part 8, when both services are containerized.

### D-021 Flyway baselineOnMigrate stays false

- Status: Accepted
- Part: 1
- Decision: Leave `baselineOnMigrate` at its default `false`. Consequence:
  never create scratch or hand-made tables in the `hospitapi` database.
- Why: Flyway's own error message suggests enabling the flag, but it would
  bless pre-existing tables as already-migrated and skip `V1__*.sql`. The flag
  exists for adopting Flyway into a legacy database, not for a schema Flyway
  owns from the start (D-009). With it off, Flyway fails fast at startup on a
  non-empty schema with no history table — which is the desired behaviour.
- Alternatives considered: `baselineOnMigrate=true` (silently skips the
  initial migration).

### D-022 Testcontainers supplies test infrastructure; no embedded database

- Status: Accepted
- Part: 1
- Decision: Integration tests obtain Postgres and RabbitMQ from
  Testcontainers via `@ServiceConnection` beans in each service's
  `TestcontainersConfiguration`, imported by the test classes that need them.
  Image tags match `compose.yaml` (`postgres:18-alpine`,
  `rabbitmq:4-management-alpine`), never `:latest`. No H2 or other embedded
  database is added, so `@DataJpaTest` requires
  `@AutoConfigureTestDatabase(replace = NONE)`.
- Why: `./mvnw test` then needs only Docker, not a prepared `hospitapi`
  database, which matters because development alternates between two
  machines. A `ConnectionDetails` bean takes precedence over
  `spring.*` properties, so tests cannot reach the compose database by
  accident. Testing against real Postgres keeps Flyway migrations free to use
  Postgres-specific SQL and exercises them from V1 on every run.
- Alternatives considered: H2 for repository tests (different dialect from
  production, forces dialect-neutral migrations); requiring `docker compose up`
  before every test run (machine-specific setup, shared mutable state).

### D-023 Formatting is left to each developer's editor

- Status: Accepted
- Part: 1
- Decision: No formatter config, tab/space rule or line width is committed to
  the repo. Each machine formats with the developer's own tooling.
- Why: Solo project with one developer on two machines; a committed config
  and a repo-wide reformat commit cost more than the occasional noisy diff.
- Alternatives considered: a one-time repo-wide reformat plus settings
  recorded in CLAUDE.md; matching Initializr's generated tabs.

### D-024 Surrogate keys are bigint identity columns

- Status: Accepted
- Part: 2
- Decision: Every table uses `id bigint generated always as identity primary
  key`, mapped with `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Why: Reviewers hand-write GraphQL operations and Postman requests against
  seeded rows, and `appointment(id: 1)` is far easier to type and read than a
  UUID. Guessable IDs also make the patient-ownership denial (D-004) easy to
  demonstrate rather than something to hide behind unguessable keys.
- Alternatives considered: `uuid` primary keys (opaque IDs, no reliance on
  authorization for obscurity, but every FK, seed row and Postman example
  becomes a 36-character literal); database sequences with an allocation size.

### D-025 Entities use JPA associations, not raw foreign-key fields

- Status: Accepted
- Part: 2
- Decision: `Appointment` holds `@ManyToOne Patient patient` and
  `@ManyToOne User doctor`; `User` holds `@ManyToOne Patient patient`. The
  planning draft's `patientId` / `doctorId` value fields are dropped.
- Why: Part 4 exposes nested GraphQL fields (`patient { name }`) and Part 5's
  event payload carries `patientName` and `patientEmail`; with raw IDs both
  turn into manual repository lookups in every resolver and publisher.
- Alternatives considered: raw ID fields with explicit lookups (no lazy-loading
  surprises, no N+1, but hand-written joins everywhere). Consequence accepted:
  lazy associations can cause N+1 in GraphQL, to be addressed with batch
  loading in Part 4.

### D-026 Doctors are User rows; only patients are a separate entity

- Status: Accepted
- Part: 2
- Decision: Add `name` to `User`. `appointments.doctor_id` references
  `users.id`. `Patient` remains the only party with contact details (email,
  phone). No `Doctor` entity and no `User.doctorId`.
- Why: Only patients receive reminders, so only patients need contact data. A
  doctor needs a display name and nothing else the brief asks for.
- Alternatives considered: a `Doctor` entity mirroring `Patient` with
  `User.doctorId` alongside `User.patientId` (symmetric, but duplicates a table
  for no requirement). Consequence accepted: no FK can enforce that
  `doctor_id` points at a user whose role is DOCTOR; that check lives in the
  service layer.

### D-027 Schema and seed data are separate Flyway migrations

- Status: Accepted
- Part: 2
- Decision: `V1__init.sql` contains DDL only; `V2__seed_data.sql` contains
  seed rows only. Seed rows insert explicit IDs with `overriding system value`
  and then advance each identity with
  `alter table <t> alter column id restart with 100`. Appointment times are
  relative (`now() + interval '3 days'`).
- Why: Splitting the files lets the schema change later without rewriting seed
  data in the same file. Explicit IDs give reviewers stable handles (patient 1,
  appointment 1) and the identity restart prevents the classic duplicate-key
  failure on the first application insert. Relative times keep "future"
  appointments future whenever the reviewer runs it, which Part 4's
  `onlyFuture` filter and Part 6's 24h job both depend on.
- Alternatives considered: schema and seed in one `V1` (fewer files, but seed
  edits churn the schema migration); Spring Boot `data.sql` (runs outside
  Flyway's version history, conflicts with D-009); a `@Profile("dev")` seeder
  bean (Java-side, invisible to `./mvnw test`).

### D-028 Custom UserDetails principal

- Status: Accepted
- Part: 3
- Decision: A `SecurityUser` record implementing `UserDetails` carries
  userId, role, and patientId; `UserDetailsService` builds it from `User`.
- Why: Ownership checks read patientId from the principal instead of
  re-querying the user on every request.
- Alternatives considered: Spring's built-in `User.withUsername(...)` plus
  a lookup by username whenever ownership matters.

### D-029 Stateless HTTP Basic, CSRF disabled

- Status: Accepted
- Part: 3
- Decision: `SessionCreationPolicy.STATELESS`, CSRF disabled.
- Why: Credentials travel with every request and no session cookie exists,
  so there is nothing for CSRF to protect. Keeping CSRF would make every
  GraphQL POST from Postman fail with 403.
- Alternatives considered: CSRF with `CookieCsrfTokenRepository`.

### D-030 Authorization lives in the service layer

- Status: Accepted
- Part: 3
- Decision: Role checks use `@PreAuthorize` on service methods; the patient
  ownership check is programmatic, inside the transactional service method.
  URL rules only distinguish public health from authenticated.
- Why: `/graphql` is one URL, so URL rules can't express D-004. Putting the
  checks on services enforces them for every caller, not just resolvers.
  Checking ownership inside the transaction avoids lazy-loading outside it,
  which `@PostAuthorize` on an entity would risk.
- Alternatives considered: `@PreAuthorize` on resolvers; `@PostAuthorize`
  with SpEL on the returned entity.

### D-031 Clinical notes edited through a dedicated doctor-only mutation

- Status: Accepted
- Part: 3
- Decision: `notes` is excluded from `CreateAppointmentInput` and
  `UpdateAppointmentInput`; it changes only through `updateClinicalNotes`,
  whose service method is `@PreAuthorize("hasRole('DOCTOR')")`.
- Why: Enforces D-004's "only DOCTOR edits clinical notes" with plain method
  security instead of field-level checks, avoids the omitted-vs-null
  ambiguity of GraphQL input fields, and makes the rule visible in the schema.
- Alternatives considered: rejecting the whole update with 403 when a nurse
  includes notes; silently ignoring notes from nurses.

## D-032 — DateTime scalar from graphql-java-extended-scalars

- **Status:** Accepted
- **Part:** 4
- **Decision:** Timestamps are exposed as `scalar DateTime` using
  `ExtendedScalars.DateTime` (graphql-java-extended-scalars 24.0), registered
  via a `RuntimeWiringConfigurer` in `config/GraphQlConfig`. A GraphQL test
  round-trips a DateTime value to guard compatibility with graphql-java 25.0 (managed by Boot 4.1.1).
- **Why:** Maps directly to the entities' `OffsetDateTime`, makes the schema
  self-describing, and follows the scalars.graphql.org DateTime spec. The
  library has no 25.x release yet; its README states 24.0+ supports 24.x and
  above, and the test turns any incompatibility into a build failure instead of
  a runtime surprise.
- **Alternatives considered:** Hand-written `Coercing<OffsetDateTime, String>`
  scalar (no dependency risk, but more code to own; fallback if the test
  fails). ISO-8601 `String` (no setup, but loses type information and pushes
  parsing into controllers).

## D-033 — Load appointment associations with @EntityGraph

- **Status:** Accepted
- **Part:** 4
- **Decision:** Repository methods that return appointments to the GraphQL layer
  declare `@EntityGraph(attributePaths = {"patient", "doctor"})`, so both
  associations are fetched in the same query, inside the service transaction.
- **Why:** Open-in-view is off and services return detached entities, so any
  lazy association a query selects must already be loaded. A fetch join per
  query is the simplest way to guarantee that, avoids N+1 selects, and keeps
  all data access inside the secured service layer (D-030).
- **Alternatives considered:** `@BatchMapping` resolvers that load patients and
  doctors in batches through a service method (more flexible if the schema
  grows, but adds loaders and extra authorization surface for a two-field
  need). Eager fetching on the entity (loads associations everywhere, including
  the security path, even when unused).

## D-034 — GraphQL integration tests on the full context via ExecutionGraphQlServiceTester

- **Status:** Accepted
- **Part:** 4
- **Decision:** GraphQL behavior is tested with `@SpringBootTest` plus
  `ExecutionGraphQlServiceTester`, against the real schema, controllers,
  service security and Testcontainers database, authenticating with
  `@WithUserDetails` against the seeded users. All integration test classes
  use the `@IntegrationTest` meta-annotation so they share one cached context.
- **Why:** Authorization lives in the service layer (`@PreAuthorize` and the
  ownership check via `SecurityUser`), so only a context with real services
  and real principals can prove it. Sharing one context keeps a single pair of
  containers per test run.
- **Alternatives considered:** `@GraphQlTest` slice with a mocked service (fast,
  but `@PreAuthorize` and ownership never run, so authorization tests pass
  trivially). `HttpGraphQlTester` with HTTP Basic (also covers the filter chain,
  but in Boot 4 needs WebTestClient; filter-chain coverage is planned for Part 7).

## D-035 — Integration tests commit; test data is isolated by owner, not rollback

- **Status:** Accepted
- **Part:** 4
- **Decision:** Integration tests are not `@Transactional`. Seeded rows are only
  read; data created by tests belongs to a test-only patient (id 900) inserted
  by `src/test/resources/sql/test-patient.sql` through `@Sql` before the class.
- **Why:** A test transaction keeps entities managed during assertions, which
  hides lazy-loading failures that D-033 guards against. With one shared
  context, owner-based isolation keeps exact-list assertions on seeded patients
  valid regardless of test class order.
- **Alternatives considered:** `@Transactional` rollback per test (masks
  detached-entity bugs). `@DirtiesContext` or a separate context per class (fresh database each time, but restarts
  containers and slows the suite).
  Relaxing assertions to `contains` (weaker tests).

## D-036 Appointment events are published after commit via a transactional event listener

- Status: Accepted
- Part: 5
- Decision: AppointmentService builds an immutable AppointmentEvent inside the
  transaction and publishes it with ApplicationEventPublisher. A
  @TransactionalEventListener (phase = AFTER_COMMIT) in AppointmentEventPublisher
  sends it to the `hospitapi.appointments` topic exchange; any send failure is
  caught (RuntimeException) and logged with the event and appointment IDs,
  never rethrown. The event carries `status` so consumers can tell a
  cancellation from a reschedule. Clinical-note edits (`updateClinicalNotes`)
  publish no event, and the event never carries notes.
- Why: Consumers never see an appointment that was rolled back, and RabbitMQ
  code stays out of the service. The payload is built inside the transaction
  because the listener runs after the persistence context closes (lazy
  `patient` would fail). Accepted trade-off: if the broker is unreachable
  right after commit, the event is lost; documented in the README.
- Alternatives considered: manual TransactionSynchronization registration (same semantics, more plumbing in the
  service); transactional outbox table
  plus relay (no loss window, but a table, poller and dedupe for a
  challenge-sized scope); publishing inside the transaction with
  channelTransacted (best-effort only, not atomic with the DB).

## D-037 Event contract is the JSON field names; each service owns its copy of the type

- Status: Accepted
- Part: 5
- Decision: Notification defines its own `AppointmentEvent` record plus
  `AppointmentEventType` and `AppointmentStatus` enums, and binds by the
  `@RabbitListener` parameter type (the converter's default INFERRED type
  precedence), ignoring scheduling's `__TypeId__` header. Unknown JSON fields
  are tolerated; an unknown enum value fails and ends up in the DLQ.
- Why: The services are standalone Maven projects with no root pom (D-005).
  Sharing Java types would couple their builds and releases. The JSON field
  names are the real contract, and tests pin it by publishing raw JSON with
  scheduling's actual type header.
- Alternatives considered: A shared contract module (needs a root pom or a
  published artifact). Consuming `Map`/`JsonNode` (no type safety). Mapping
  type ids between the two class names (keeps the coupling through class names).

## D-038 Notification is stateless; no event deduplication

- Status: Accepted
- Part: 5
- Decision: Notification has no database and does not deduplicate by
  `eventId`. Under at-least-once delivery, a redelivered event can produce a
  duplicate reminder.
- Why: Reminders are logged (mocked), so a duplicate costs nothing. Real
  dedupe needs persistent storage, and adding Postgres and Flyway to
  notification isn't justified by the brief.
- Alternatives considered: A processed-events table in a notification-owned
  database. A local appointments table that would also feed Part 6's job. An
  in-memory set (rejected: lost on restart and not shared between instances,
  so it only looks like idempotency).
- Consequence: Part 6's scheduled job has no local data. It must query
  scheduling, or a later decision superseding this one adds persistence.

## D-039 Consumer declares the shared exchange and owns its queue, DLX and DLQ

- Status: Accepted
- Part: 5
- Decision: Notification declares `hospitapi.appointments` (topic, durable)
  identically to scheduling, plus its own `notification.appointment-events`
  queue (with dead-letter arguments), the `notification.dlx` direct exchange
  and the `notification.appointment-events.dlq` queue. Listener retry uses
  `spring.rabbitmq.listener.simple.retry.max-retries=2` (3 attempts total)
  with Boot's default reject-without-requeue recoverer, which dead-letters.
- Why: Declarations are idempotent, and binding to a missing exchange fails,
  so declaring it in both services lets either start first (verified
  manually). Each consumer owns its failure handling.
- Alternatives considered: Declaring the exchange only in scheduling and
  requiring startup order in compose and the README. A single DLX shared by
  all consumers.

  ## D-040 The 24-hour reminder job runs in scheduling and publishes REMINDER_DUE events

- Status: Accepted
- Part: 6
- Decision: A @Scheduled job in scheduling selects appointments with status
  SCHEDULED, `scheduledAt` in [now, now + 24h) and `reminder_sent_at` null.
  In one transaction it sets `reminder_sent_at` and publishes an
  AppointmentEvent of type REMINDER_DUE (routing key
  `appointment.reminder-due`) through the after-commit path of D-036.
  Notification handles it with its existing listener and ReminderSender and
  stays stateless. Due rows are read with a pessimistic write lock that
  skips locked rows, so overlapping runs or instances never claim the same
  appointment. Changing `scheduledAt` clears `reminder_sent_at`. Time comes
  from the Clock bean. The interval and window are properties. The job is
  switched by `hospitapi.reminders.enabled` (default true, false in tests).
  This corrects D-038's consequence note, which assumed the job needed data
  in notification. D-038's decision itself is unchanged.
- Why: Scheduling owns the appointment data, so dedupe is one column next to
  it. No service-to-service authentication, no persistence in notification,
  and the tested publish path and consumer are reused. Accepted trade-offs:
  the job inherits D-036's loss window (if the broker is unreachable right
  after commit, `reminder_sent_at` is set and that reminder is lost, not
  retried). An appointment created less than 24h ahead gets both its
  CREATED reminder and a REMINDER_DUE one.
- Alternatives considered: Job in notification querying scheduling over
  GraphQL or HTTP (needs a service account outside D-004's roles, and still
  needs dedupe state, so persistence anyway). A notification-owned read model
  built from events, superseding D-038 (database, Flyway, stale-event
  handling and backfill for an optional part). ShedLock for multi-instance
  safety (extra dependency and table; row locks cover it). Setting
  `reminder_sent_at` only after a publisher confirm (closes the loss window,
  but puts RabbitMQ into the job and departs from D-036).