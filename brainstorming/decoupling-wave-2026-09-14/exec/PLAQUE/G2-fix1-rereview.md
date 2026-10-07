# Re-review — Plaque G2 fix round 1 — 2026-09-17 (Sonnet, transcribed)
I1 ADDRESSED (`Plaque.java:36` holds ViaAPI, set :84; `BoardManager.java:33,40-41` takes `Plaque`, reads `plaque.getViaAPI()` in a body :54; `Plaque` is `registerInstance`d (`PlaqueContext.java:58`), not `@Bean`/listener-scanned) · I2 ADDRESSED (`ReloadCommandTest.java:47-50` InOrder, red run recorded) · M3 ADDRESSED (`BoardSettings.java:54-58`) · M4 ADDRESSED (`BoardManager.java:91-94`; test :49-73) · M5 ADDRESSED (`README.md:39-42`).
New breakage: none (BoardConfig bean, tests, listeners, Board ctor all consistent).
Verdict: all addressed.
