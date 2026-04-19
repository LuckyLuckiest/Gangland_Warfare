# Ranks — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/ranks.md)

---

## Overview

Hierarchical rank/permission system with parent-child relationships. Managed by `RankManager`. Ranks can be created
at runtime via commands; promotion within a gang walks the rank tree.

**Modules involved:** `gangland-impl`.

---

## Pre-Conditions

- [ ] Op player online.
- [ ] An existing gang with at least two members at different ranks.

---

## Smoke Test

- [ ] `/glw rank list` → shows all configured ranks.
- [ ] `/glw rank create Enforcer` → new rank appears.
- [ ] `/glw rank info Enforcer` → shows empty permissions + no parents.
- [ ] `/glw rank permission add Enforcer glw.gang.kick` → permission added.
- [ ] `/glw rank parent add Enforcer Member` → `Enforcer` inherits from `Member`.
- [ ] `/glw rank traverse` → shows next rank(s) in the hierarchy.
- [ ] `/glw rank permission remove Enforcer glw.gang.kick` → permission removed.
- [ ] `/glw rank parent remove Enforcer Member` → parent link removed.
- [ ] `/glw rank delete Enforcer` → rank gone.

---

## Edge Cases

- [ ] Delete a rank that has members → either migrate them to parent rank or reject — verify expected policy.
- [ ] Circular parent chain (`A → B → A`) → rejected at parent-add time.
- [ ] Rank name with spaces / special chars → rejected or sanitised.
- [ ] Grant a permission a rank already has → no duplicate; command is idempotent.
- [ ] Check permission inheritance: member with parent rank `Member` passes a permission check that is only on
  `Member`.

---

## Reload Safety

- [ ] Create a custom rank, run `/glw reload` → rank survives.
- [ ] Edit rank permissions via YAML + `/glw reload files` → changes apply.

---

## Persistence

- [ ] Ranks, permissions, parent links persist across restart.
- [ ] Member's assigned rank persists.

---

## Regression Risks

- `RankManager` — CRUD + traversal.
- Gang promote/demote — relies on rank graph.
- Permission check short-circuits — ops bypass; non-ops check rank + parents.

---

[Back to Test Index](../README.md)
