# Scoreboard — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/scoreboard.md)

---

## Overview

Per-player live scoreboard rendered via FastBoard. Layout and placeholder drivers defined in `scoreboard.yml`.
Lifecycle managed by `ScoreboardLifecycleService`.

**Modules involved:** `gangland-ui/scoreboard-api`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Player `A` online with scoreboard enabled in settings.
- [ ] `scoreboard.yml` present with animated title + at least three driver lines.

---

## Smoke Test

- [ ] Join server → scoreboard appears immediately.
- [ ] Animated title cycles through its frames at the configured rate.
- [ ] Each driver line (balance, gang, wanted stars, level, etc.) shows correct placeholder values.
- [ ] Change a driver's underlying value (e.g. `/glw economy deposit A 100`) → line updates within one tick.
- [ ] Toggle scoreboard off/on per player (if a toggle exists) → hides and re-renders correctly.

---

## Edge Cases

- [ ] Colour codes in `scoreboard.yml` use `&`, not `§` (`feedback_chat_color_codes`).
- [ ] Line longer than 40 characters → truncated gracefully (FastBoard cap).
- [ ] PlaceholderAPI missing → placeholders fall back to raw text, no stack trace.
- [ ] Join + immediate logout (sub-second) → no leaked FastBoard instance.

---

## Reload Safety

- [ ] Edit a line in `scoreboard.yml` → `/glw reload scoreboard` applies change to all online players.
- [ ] Full `/glw reload` also re-renders scoreboards without duplication.
- [ ] Reference: `project_copmanager_reload_npe` / `BeanPostInitialize` contract — `ScoreboardLifecycleService` was
  migrated off `BeanLifecycle` to avoid reload NPEs.

---

## Persistence

- [ ] Scoreboard has no persistent state; nothing to verify across restart beyond "it still renders after reboot."

---

## Regression Risks

- `ScoreboardLifecycleService` — per-player lifecycle (attach on join, detach on leave).
- FastBoard version compatibility across MC 1.10–1.21 NMS adapters.
- Placeholder service — `PlaceholderService` bean (see `project_decoupling_phases`).

---

[Back to Test Index](../README.md)
