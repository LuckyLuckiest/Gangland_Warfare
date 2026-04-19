# <Feature Name> — Test Checklist

[Back to Test Index](./README.md) | [Feature Doc](../features/<feature>.md)

---

## Overview

One paragraph: what this feature does and which modules it touches. Link to the feature doc at
`documentation/features/<feature>.md` for user-facing behaviour and the developer doc at
`documentation/developer/<feature>.md` for internals, if either exists.

**Modules involved:** `gangland-impl`, `<feature-module>`, …

---

## Pre-Conditions

Things that must be true before running the checklist. Delete rows that do not apply.

- [ ] Server is running the new build.
- [ ] At least two online players (one op, one regular).
- [ ] Database mode: run the checklist once on **SQLite**, once on **MySQL** (see
  [persistence-and-database.md](../persistence-and-database.md)).
- [ ] Relevant config files exist at their default paths (regenerate by deleting them and restarting if unsure).

---

## Smoke Test (Golden Path)

The happy-path interactions a user would perform. Each step should be a single action with a visible, verifiable
outcome.

- [ ] Step 1 — `<command or action>` → expected result.
- [ ] Step 2 — …
- [ ] Step 3 — …

---

## Edge Cases

- [ ] Offline target: run the command against a player who is not online → expected graceful handling.
- [ ] Missing permission: run as a non-op player without the rank permission → expected denial message.
- [ ] Invalid input: pass a malformed argument → expected parse-error message, no stack trace.
- [ ] Mid-operation reload: start a long-running operation, run `/glw reload` before it completes → expected behaviour.
- [ ] Concurrent use: two players run the command at the same time → no race condition.

---

## Reload Safety

- [ ] Run `/glw reload` while the feature is in use. All active state survives or is cleanly torn down.
- [ ] No duplicate listeners after reload (fire the event once, confirm one handler response).
- [ ] Feature's YAML config changes take effect after reload without a full restart.

---

## Persistence

- [ ] Create some feature state (e.g. a record, an entity, a bound config).
- [ ] Stop the server cleanly (`stop` command).
- [ ] Restart the server → state is restored.
- [ ] Repeat with the database in MySQL mode if the feature persists to DB.

---

## Regression Risks

List modules or subsystems this feature depends on. If any of them changed this release, take extra care here.

- `<module-1>` — …
- `<module-2>` — …

---

[Back to Test Index](./README.md)
