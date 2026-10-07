# Re-review — WS7 G4 fix round 1 — 2026-09-17 (Sonnet, transcribed)
I1 ADDRESSED (GadgetFileConfig.java:66-74 gadget_messages under gadget/) · I2 ADDRESSED (both give commands guard + clamp; red run NegativeArraySizeException) · I3 ADDRESSED (JetpackService.java:53 silent; JetpackEquipListener.java:60,78,95-102; lifecycle/activate listeners send nothing) · M1 ADDRESSED (commands.json:26-29) · M2 ADDRESSED (JetpackLegacyMigrationTest.java:139-174).
New breakage: none. Verdict: all addressed.
