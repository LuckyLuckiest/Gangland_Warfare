# WS3 G4+G5 fix round 1 — 2026-09-22/23

Status: DONE (ruling W53, 3 Important findings from the G4-G5 review)

Worktree: `E:\Programming\java\wt\gangland-0.10.0-ws3`, still at `5b915c17`, nothing committed. Cumulative diff (all
of G4+G5 plus this fix round) at `exec/WS3/G4-G5-fix1-package.diff`.

## F1 — Spanish loot-chest strings were dropped

**Finding**: `message_es.yml` lost its 3 `Loot_Chest` blocks in G4 and the module shipped English-only; the
Spanish translation was silently lost instead of moved.

**Fix**, file:line by file:line:

- `gangland-features/gangland-lootchest/src/main/resources/lootchests/lootchest_messages_es.yml` (new file, 26
  keys) — the exact 26 Spanish strings `git diff HEAD -- gangland-impl/.../message_es.yml` showed as deleted,
  copied verbatim (same leaf key names as the English `lootchest_messages.yml`, confirmed by diffing the two
  files' key sets).
- `gangland-features/gangland-lootchest/.../config/GanglandLootChestMessages.java:1-45` — constructor rewritten
  from a single hard-coded `"lootchest_messages"` read into `resolveFileHandler(FileManager)`: if
  `Settings.getLanguagePicked()` (`gangland-api`'s existing knob — same one Keystone's `LanguageLoader` already
  uses to choose core's own `message_<lang>.yml`, found via `FileConfig.languageLoader(...)`'s
  `Settings::getLanguagePicked` reference, not invented here) equals `"es"`, try the Spanish `FileHandler`
  (`lootchest_messages_es`) first; otherwise, or if that file isn't registered, fall back to the English
  `FileHandler` (`lootchest_messages`). `tryGetFile` wraps `checkFileLoaded`/`getFile` in a try/catch returning
  `null` on `IOException`, so an absent file degrades to the fallback instead of throwing.
- `gangland-features/gangland-lootchest/.../config/LootChestFileConfig.java:38-39` — registers the new
  `lootchest_messages_es` `FileHandler` (same `lootchests/` folder, same `moduleLoader.classLoader()` pattern as
  the other 4) alongside the existing ones, so the real bootstrap path has the file available, not just the test
  fixture.

**Red-first evidence**: wrote `GanglandLootChestMessagesTest.java` (new file, 3 tests) *before* touching
`GanglandLootChestMessages.java`'s constructor.
- Red: `mvn -pl gangland-features/gangland-lootchest -am test -Dtest=GanglandLootChestMessagesTest
  -Dsurefire.failIfNoSpecifiedTests=false` →
  `GanglandLootChestMessagesTest.languageEs_resolvesSpanishFile:40 expected: <marcador-es> but was: <marker-en>`
  (`Tests run: 3, Failures: 1` — the other 2 tests, which expect English, passed trivially against the
  unfixed single-file reader, exactly as expected).
- Green (same command after the fix): `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

The marker read is `getTimeMessages().getYear()` — a `Type.NO_CHANGE`-style raw string with no
`GanglandChatUtil` prefix wrapping — deliberately, so the test doesn't also need `Messages.init(...)` wired for
`ERROR_PREFIX`/etc.

### Deviations (F1)

- **3 new tests, not 1.** The fix's own description calls for "a test where the language is `es` returns the
  Spanish string" (the red-first one, `languageEs_resolvesSpanishFile`). I added two more:
  `languageOther_fallsBackToEnglish` (a non-`"es"` language, e.g. `"fr"`, uses English) and
  `languageEs_missingSpanishFile_fallsBackToEnglish` (language is `"es"` but the Spanish `FileHandler` was never
  registered — exercises the `tryGetFile` null-fallback branch specifically). Both were green-on-arrival (they
  assert the pre-existing, unchanged English-fallback behaviour), not additional red-first pins — but they're the
  only coverage of the "falling back... when absent" half of F1's own requirement, which the single required test
  doesn't reach. Flagging since this moved the reactor total more than a literal 1-test reading of the brief
  would predict (see Build section).
- The migration doc's WS3 section (§1, key-map table) updated: the `translate the new file yourself` sentence
  removed, both languages now listed as shipping, one new sentence naming the `Settings.getLanguagePicked()`
  reuse. Not itemized as its own fix in the brief's F1 text but explicitly requested in the same paragraph.

## F2 — genuine red for the 2 `SettingsTest` legacy-warning cases

**Finding**: the G4 report claimed red/green evidence for
`initialize_legacyLootChestBlock_logsTargetedMigrationWarning`/`initialize_noLegacyLootChestBlock_...` without an
actual mutate-and-rerun — it inferred correctness from the WS4 Trader/Banker precedent instead of checking this
specific call site.

**Genuine red-check performed**:
1. `gangland-api/src/main/java/.../Settings.java:733-735` — the 3-line
   `warnIfLegacyShopBlockPresent(section(root, "Loot_Chest", ...) ...)` call commented out (production body
   otherwise untouched).
2. Rebuilt: `mvn -pl gangland-api,gangland-impl -am test -Dtest=SettingsTest
   -Dsurefire.failIfNoSpecifiedTests=false` (the `-am` flag recompiles `gangland-api`'s reactor classes so
   `gangland-impl`'s test classpath picks up the mutation without a separate `install` step).
3. **Red**: `SettingsTest.initialize_legacyLootChestBlock_logsTargetedMigrationWarning:186 expected a targeted
   migration warning naming the module's own settings file; got: [settings.yml:1:1 at Loot_Chest | unknown key
   'Loot_Chest' [config.unknown_key]] ==> expected: <true> but was: <false>` (`Tests run: 10, Failures: 1`) — with
   the call site gone, a leftover `Loot_Chest:` block falls through to the generic "unknown key" line instead of
   the targeted one, exactly the pre-fix behaviour the test is meant to catch.
4. Restored `Settings.java` from a pre-mutation copy; `diff` against the backup confirmed byte-identical.
5. **Green**: `mvn -pl gangland-api,gangland-impl -am test -Dtest=SettingsTest
   -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`.

## F3 — individual mutation of `handleInvSizeChange` and `openConfigInventory`

**Finding**: the G4 report's W50 pins for these two methods were asserted correct "by mechanism" (same guard
shape as the independently-red-checked `setWandNBT`), without individually mutating each one.

**`handleInvSizeChange`** (`gangland-features/gangland-lootchest/.../LootChestWand.java:366`):
1. Line 366 changed from `player.getInventory().getItem(wandSlot)` to `player.getInventory().getItemInMainHand()`
   (only this line; `setWandNBT`/`openConfigInventory` left untouched for this run).
2. Red: `mvn -pl gangland-features/gangland-lootchest -am test -Dtest=LootChestWandTest
   -Dsurefire.failIfNoSpecifiedTests=false` →
   `LootChestWandTest.handleInvSizeChange_resolvesTargetFromCapturedWandSlot_notFromCurrentMainHand:145` fails
   (`Tests run: 4, Failures: 1`) — the other 3 (including `openConfigInventory`'s own pin) stayed green,
   confirming this mutation isolates `handleInvSizeChange` specifically.
3. Reverted from a pre-mutation copy; `diff` confirmed byte-identical.
4. Green: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

**`openConfigInventory`** (`gangland-features/gangland-lootchest/.../LootChestWand.java:115`):
1. Line 115 changed the same way, alone this time (`handleInvSizeChange` back to its original, untouched line).
2. Red: same command →
   `LootChestWandTest.openConfigInventory_resolvesTargetFromCapturedWandSlot_notFromGetItemInMainHand:166` fails
   (`Tests run: 4, Failures: 1`) — the other 3 stayed green.
3. Reverted; `diff` confirmed byte-identical.
4. Green: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

`git diff --stat -- .../LootChestWand.java` against the worktree's tracked baseline (`5b915c17`) shows **no
output** — this file was never part of any G4/G5/fix-round production edit; every mutation above was written,
verified, and reverted in place, leaving the file exactly as it already was in the reviewed commit.

## Docket note (per the ruling)

Added to the WS3 docket notes (this report; no db write, matching G5's existing "notes for the clerk" convention):

> module-owned message localisation is ad hoc here; WS6 G3's mechanism generalises it

## Build

Full reactor (one build, `mvn clean verify`, never `install`) → **`BUILD SUCCESS`**, 01:02 min, 0 failures/errors.

**Test count (Maven console rollup, per W52)**:

| Module | Tests run |
|---|---|
| gangland-core | 67 |
| gangland-infra/gangland-item | 43 |
| gangland-ui/sign-api | 63 |
| gangland-infra/gangland-domain | 63 |
| gangland-impl | 260 |
| gangland-features/gangland-mail | 25 |
| gangland-features/gangland-civilians | 20 |
| gangland-features/gangland-turf | 91 |
| gangland-features/cops-n-crooks | 76 |
| gangland-features/gangland-gadget | 113 |
| gangland-features/gangland-npc-shops | 17 |
| **gangland-features/gangland-lootchest** | **53** |
| **Total** | **891**, 0 Failures, 0 Errors, 0 Skipped |

**Delta vs. the G4-G5 gate's 888: +3, not the gate's stated "+1" expectation.** All +3 are
`GanglandLootChestMessagesTest` (F1's new file); every other module is byte-for-byte unchanged from the previous
gate (`gangland-lootchest` itself: **50 → 53**). F2 and F3 added zero persisted tests by design — both are
one-off mutate/revert verification runs of *already-existing* tests, not new test methods. The +3-vs-+1 gap is
explained in full in F1's own Deviations note above (2 of the 3 are fallback-path coverage beyond the single
required red-first test).

## Deliverables

- `exec/WS3/G4-G5-fix1-report.md` (this file).
- `exec/WS3/G4-G5-fix1-package.diff` — cumulative (`git add -N . && git diff HEAD`, then `git reset`; nothing
  staged or committed). 40 files changed vs. `5b915c17`.

No smoke run, no `scenarios.json`, no commits, no reviewers invoked.
