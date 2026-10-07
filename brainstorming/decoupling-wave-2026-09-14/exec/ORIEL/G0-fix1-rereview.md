# Re-review — Oriel G0 fix round 1 — 2026-09-16 (Sonnet, transcribed)
C1 ADDRESSED (`OrielPlugin.java:208-210` publishes the three registries from the DI container; `MenuConfigService.forConsumer` :196-214 requires them; `KernelConfig.java:187-190` binds `PaginatedControlActions` to the same bean) · I2 ADDRESSED (`RequirementParser.java:99-116` ponytail notes, logic unchanged, idempotent re-install) · I3 ADDRESSED (red `expected: not <null>` for the new filter test; spike-(a) red `expected: 999 but was: 1`) · javadoc ADDRESSED (:150-194).
New breakage: none (registerServices runs after bootstrap so the DATABASE-phase CooldownService exists; production publishes all three, not only the test fixture; unregisterAll sweeps them on disable).
Verdict: all addressed.
