# Orchestrator decisions (Fable) — 2026-09-10

## Bartizan: independent second read (feature-dev code-reviewer, opus) of all 19 P0/P1
All 19 CONFIRMED as real. Tier changes applied after weighing the lead's tier against the reviewer's suggestion:
- bartizan/commands #1: P1 -> P2 — crash only on nonsense input to a permission-gated admin command; zero-amount false success is bounded
- bartizan/config-parsing #1: P1 -> P2 — failure is still printed (info level), so not silent; admin-visibility gap -> scheduled
- bartizan/firing-actions #3: P1 -> P2 — no shipped melee/throwable YAML carries Ammunition/Reload -> dead config feature, not wrong normal-use behaviour
- bartizan/persistence #1: P1 -> P2 — deleteAll works on a normal boot; only the failed-MySQL path hits the null legacy handle -> latent trap
- bartizan/weapon-model #1: P1 -> P2 — every shipped YAML has a Modifiers section -> authoring trap
- bartizan/weapon-model #3: P1 -> P2 — only rocket_launcher uses num-N reload today -> authoring trap
Kept at the lead's tier despite no reviewer objection: BZ-RT-01, BZ-RT-02, BZ-WE-01 (P0); BZ-WM-02, BZ-WM-04, BZ-WM-06, BZ-FA-01, BZ-FA-02, BZ-FA-04, BZ-RT-03, BZ-EV-01, BZ-EV-02, BZ-NU-01 (P1).

## Oriel: independent second read (feature-dev code-reviewer, opus) of all 14 P0/P1
All 14 CONFIRMED as real. Tier change applied:
- oriel/menu-editor #2: P1 -> P2 — needs grab -> edit-the-grabbed-cell -> shift-click sequence against the working copy; edge-case trap, not normal use
Reviewer note kept for the fixer (no tier impact): OR-CR-01's collision example is wrong (safeFileName keeps '-' so 'Shop-A' does not collide with 'Shop A'); the truncate-without-existence-check bug stands.
Kept at the lead's tier: OR-CH-01, OR-CH-02, OR-IV-01, OR-DB-01, OR-DI-01, OR-DI-02 (P0); OR-MC-01, OR-CR-01, OR-ED-01, OR-CH-03, OR-IV-02, OR-DB-02, OR-DP-01 (P1).

## Keystone: independent second read (feature-dev code-reviewer, opus) of the 28 P0/P1 from the 11 systems finished before the session-limit outage
27 CONFIRMED, 1 DISPUTED on consequence (npc-system #3). Tier changes applied:
- keystone/bean-container #1: P1 -> P2 — resolveParameter type-checks named lookups, so only a same-name same-type collision is silent -> latent trap
- keystone/bean-container #2: P1 -> P2 — needs a mid-class registerEvent failure (malformed event class), not a normal path
- keystone/command-framework #2: P1 -> P2 — corruption only when a confirm sibling sits at the same depth as a free-text node -> edge case
- keystone/config-file #3: P1 -> P2 — reads go through NodeReader which strips underscores; only the serialize round trip rewrites the number as a string -> fidelity trap
- keystone/database-repos #2: P1 -> P2 — no shipped consumer reaches the stale snapshot today (setType resolves before the repository scan) -> latent trap
- keystone/diagnostics-error #4: P1 -> P2 — fires only on a server build where the reflective handles do not wire -> version-specific edge crash
- keystone/module-loader #1: P1 -> P2 — needs a marker-flagged jar still locked at boot -> edge case, though the consequence (zero modules load) is severe on Windows dev servers
- keystone/npc-system #2: P1 -> P2 — needs a consumer NpcRangedAttack.onDestroy that throws -> latent trap
- keystone/npc-system #3: P1 -> P3 — reviewer disputed the consequence: tickLadderClimb resumes when the entity is non-null again and MAX_LADDER_CLIMB_TICKS forces endLadderClimb, so a transient null costs a delay, not a permanent stall
Kept at the lead's tier: hooks-integration #1 (P0, EconomyHandler.setAmount non-atomic withdraw+deposit); command-framework #1 #3 #4, config-file #1 #2, database-repos #1 #3, diagnostics-error #1 #2 #3, hooks-integration #2, module-loader #2, npc-system #1 #4, scheduling-cooldowns #1 #2 #3 #4 (P1).
utils-helpers and the five cross-project root-cause checks arrived after this review; see the next section.

## Oriel: CR second pass (5 new, OR-CR-05..09) — verified by Fable directly
OR-CR-05, OR-CR-06, OR-CR-07 (P1): cited lines re-read (MenuConfigService.registerMenu 269-273 registers the Supplier without calling it; ChestMenuBuilder.build 313-316 is the only slot range check; ChestMenuLoader 200/484 use .min(0) only vs SlotLoader 56-60; ItemDefinitionWriter.isNamespacedReference 139-145 checks only ':' while ItemDefinitionLoader 412-416 dispatches on '-'). All confirmed at P1.
The lead reverted menu-editor #2 to P1 while reconciling its totals; the orchestrator's P2 decision (reviewer-backed) stands and was re-applied. Oriel totals are therefore 37 = P0 6 / P1 10 / P2 19 / P3 2.

## Keystone: post-outage additions (utils-helpers + five cross-project root causes) — verified by Fable directly
Cited lines re-read for every new P0/P1: KS-DB-10 (AbstractJdbcBackend.applySchema 132-137 + SchemaDiff toDrop), KS-DB-12 (DatabaseHandler.setType/useSQLite early return), KS-DB-13 (AbstractRepository.saveAll 121-131 snapshot on caller thread), KS-CF-07 (NodeReader.asDouble 255-257 + DoubleAccess.min 492), KS-SC-01 (Timer.start/stop 49-66; BukkitRunnable.cancel never clears its task field), KS-UC-01 (ResourcePackTracker static active/install), KS-UC-02 (PdcPlayerMetaService.set/remove early return). All confirmed at the lead's tier; KS-SC-01 accepted at P0 (crash on a normal reload/toggle path in every consumer).
The lead's final reconciliation re-applied its own tiers over the nine reviewer-backed decisions above; those nine were restored (BN-01, BN-02, CM-02, CF-03, DB-02, DG-04, MO-01 -> P2; NP-02 -> P2; NP-03 -> P3).
Lead open questions: KS-CM-01 stays P1 (discloses syntax, executes nothing); KS-HK-02 stays P1 (needs a consumer passing a negative delta; Keystone's defect is the missing boundary check); the four legacy-stack (@Deprecated since 1.6.0) database findings stay tiered on merit because Gangland still routes autosave through that stack; the three javadoc layering entries stay separate (different files, different owners).

## Cross-project dedup rules applied by build_docket.py
- A non-Gangland observation containing "was Gangland WP-nn" marks the Gangland row status `moved` (note names the BZ id).
- "also Gangland CL-nn" / "also Oriel OR-xx" leaves both rows open and adds a note naming the twin; root cause is fixed once, in Keystone.
