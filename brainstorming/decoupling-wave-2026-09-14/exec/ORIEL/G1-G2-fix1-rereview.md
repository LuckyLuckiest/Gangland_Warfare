# Re-review — Oriel G1+G2 fix round 1 — 2026-09-17 (Sonnet, transcribed)
C1 ADDRESSED (`ChestMenu.java:477-492` returns via `returnHeldItemsForSlot` :713-740 before clearing; no double-return path) · I2 ADDRESSED (`MenuFlow.java:121-128`; test :133-153) · I3 ADDRESSED (:165-182 row-count guard; test `MenuFlowTest:168-189`) · I4 ADDRESSED (`PriceGridComponent.java:75-104`; two tests) · I5 ADDRESSED as ruled (reading-only statement) · M6 ADDRESSED.
New breakage: none (same-id `switchTo` no longer fires on_open/on_close — no production caller uses it today; callout for future consumers).
Verdict: all addressed.
