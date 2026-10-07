# Review — Gangland 0.10.0 WS3 G4+G5 (module-owned config/messages/commands, lootchest-api deleted) — 2026-09-22 (Sonnet, transcribed)
Verdict: FIX (3 Important).

## Spec table (condensed)
1 config migration ⚠ byte-exact for the 10 settings / 26 English messages (verified line-for-line) but Spanish dropped (F1) · 2 legacy warning ⚠ code/text correct, WS4 texts unchanged, red missing (F2) · 3 injection ✅ providers constructor-injected in listeners + 3 commands, no statics, commands.json delta 153→149 checks · 4 G5 deletions ✅ zero external `org.luckyraven.gangland.lootchest` / `Messages.LOOT_CHEST` / `Settings.getLootChest` references · 5 W50 pins ⚠ present, production byte-identical, 2 of 3 wand pins without an individual red (F3) · 6 docs ✅ · 7 deviations ✅ (`lootchests/` matches the `npc/*.yml` convention; helper generalisation fine).

## Findings
**F1 Important** — `message_es.yml`'s three `Loot_Chest` blocks (26 keys) deleted; the module ships `lootchest_messages.yml` in English only with no locale switch → an `es` server silently gets English. Documented in migration-0.10.0.md ("translate yourself") but not authorised and not in the report's deviations.
**F2 Important** — the 2 new `SettingsTest` Loot_Chest cases (:173-198) have no red run (WS4 G1a's fix1 precedent commented the call site out, rebuilt, showed the failure).
**F3 Important** — of the 3 LS-30 pins only `setWandNBT_…` (:102-123) was mutation-tested; `handleInvSizeChange_…` (:125-146) and `openConfigInventory_…` (:148-168) were not individually red-checked.

## Cannot verify
Reactor 888/0, module jar contents, live `/glw reload` re-read (shape matches JetpackMessages).

## Rebase notes vs 0.10.0-ws5
Shared files edited here: settings.yml (`Loot_Chest:` before `Money_Drop:`), message_en/es.yml, commands.json, Settings.java (10 getters + parse block; helper widened), Messages.java (26 constants), InformationManagerTest, SettingsTest, root pom (2 dependencyManagement entries), gangland-ui + gangland-impl poms, CLAUDE.md, migration-0.10.0.md. No gangland-build edit this gate.

## Orchestrator rulings (W53)
F1: the module ships `lootchest_messages_es.yml` with the deleted Spanish strings and the reader picks the file by the core language key (fallback en), red-first; docket note "module-owned message localisation is ad hoc; WS6 G3 generalises". F2/F3: repeat the mutate/rebuild/revert reds and record. Fix round dispatched to the WS3 lead.
