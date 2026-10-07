# Review — Gangland 0.10.0 WS6 G3 (module-owned messages mechanism, civilians worked example) — 2026-09-23 (Sonnet, transcribed)
Verdict: PASS (1 Minor).

## Spec table (condensed)
11 `LocalizedModuleYaml` = line-for-line generalisation of `GanglandLootChestMessages.resolveFileHandler/tryGetFile`, api-only imports, no VERSION bump ✅ · 12 migration table with T-47; stray `Messages.BANKER_DAILY_DEPOSIT_REACHED` at `BankDepositCommand.java:115` confirmed (pre-split leftover, belongs to a future BANKER migration gate) ✅ · 13 static-vs-instance per §9 D4/D8 ✅ · callers 12→10 files by DI, zero `Messages.CIVILIAN_` left ✅ · legacy warning from `Messages.init` via the same-package helper, once per init, no duplicate sweep to clash with ✅ · bean ordering: Settings is FILE-phase, `CiviliansModuleConfig` CONFIG ✅ · `npc/civilian_messages.yml` per plan C6 (reuses the civilians pipeline), registered name avoids the bare-"messages" collision (W22) ✅ · es parity spot-checked byte-identical ✅ · style ✅.

## Findings
M1 — `CivilianMessagesTest.initMessagesProvider()` stubs `getString` to "" (non-null), so `Messages.init()`'s civilian legacy probe logs the warning in each of the 3 runs — log noise only. Left as is (orchestrator).

## Cannot verify
Reactor 896/0 and the three genuine reds (accepted from the report).

## Rebase notes vs 0.10.0-ws5
Low risk: ws5 touches civilians' pom + `listener/gang/GangAllyWeaponImpactListener.java` only; ws5 has not moved GANG_/RANK_ constants, so no Messages.java conflict today.
