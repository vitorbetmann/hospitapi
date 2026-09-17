## Demo credentials

Seeded by `scheduling/src/main/resources/db/migration/V2__seed_data.sql`.
Demo data for reviewers — these are not real credentials and the database
holds no real patient data.

| Username  | Password     | Role    | Linked patient |
|-----------|--------------|---------|----------------|
| `doctor`  | `doctor123`  | DOCTOR  | —              |
| `nurse`   | `nurse123`   | NURSE   | —              |
| `patient` | `patient123` | PATIENT | 1 — Ana Souza  |

Passwords are stored as bcrypt hashes (cost 10) in the `{bcrypt}$2a$...`
form expected by Spring Security's delegating password encoder.