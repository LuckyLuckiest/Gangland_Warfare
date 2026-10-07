# Review — Plaque G0+G1 — 2026-09-16 (Sonnet, transcribed)
Verdict: APPROVED (no finding ≥ Important)
Spec: repo skeleton ✅ · pom `${revision}`+flatten+Central, Keystone provided, FastBoard shaded+relocated (`pom.xml:95-180`, `:239-273`) ✅ · plugin.yml verbatim ✅ · empty-plugin build ✅ · engine ported byte-for-byte apart from rename + the two named fixes ✅ · C4 `PlaceholderProvider` ✅ · UI-01/UI-02 intact ✅ · UI-17 guard `Line.java:82-83,88-89` red-first ✅ · DriverV1/V2/reflection scan gone ✅ · tests ported + new UI-17 case ✅ · no `org.luckyraven.gangland`, no Settings/Messages, no Paper, floor-compile proof ✅
Cannot verify: shaded jar contents; FastBoard 2.1.5 on a live 1.16.5 server (G-final smoke).
Note: WS1-D2 = keystone-command is the user's decision (ruling W5) and overrides the plan's `CommandExecutor` recommendation — confirmed to the lead at the G2 dispatch.
