# 0.16.1 T-190: radio speaker names (lane report)

Branch `cnc-0.16.1-radio`, worktree `E:/Programming/java/wt/cnc161-radio`. Owner ruling: radio names keep their
colours, the line's colour resumes after the name, and the name format is configurable in the module's own YAML.

## Key

- File: `gangland-features/cops-n-crooks/src/main/resources/copsncrooks/cop_radio_messages.yml`, root key `Speaker_Name`
  (Spanish `cop_radio_messages_es.yml` carries its own copy of the key).
- Code default: `CopRadioMessages.DEFAULT_SPEAKER_NAME` = `{rank} {role} &f{name} &7#{number}`.
- Placeholders:
  - `{rank}`: tier `Display_Name`, colour kept.
  - `{role}`: role word in its colour, no symbol. Empty for a role-less cop.
  - `{name}`: first name from `Cops.Names.First_Names`. Empty when that list is empty.
  - `{number}`: badge number, `1000 + NPC id`.
- Empty placeholder: its colour code goes with it and runs of spaces collapse.
- Line colour: the trailing colour codes before `%line%` in `Format` are appended to the name, so the line resumes its
  colour after the name.
- Rank-and-name example for owners: `Speaker_Name: "{rank} {name}"`.

## Before and after

Prefix `&9&l[RADIO] &b%unit%&8: &7%line%`, line `Copy.`

| Cop | Before (visible) | After (visible) | After (raw speaker name) |
|---|---|---|---|
| A: `&9Officer`, Medic `&c✚ Medic`, Bob, id 592 | `Officer Medic Bob #1592` (all aqua) | `Officer Medic Bob #1592` (rank, role and number keep colours) | `&9Officer &cMedic &fBob &7#1592&7` |
| B: `&1&lSWAT`, Marksman `&2⌖ Marksman`, Tony, id 204 | `SWAT Marksman Tony #1204` (all aqua) | `SWAT Marksman Tony #1204` | `&1&lSWAT &2Marksman &fTony &7#1204&7` |
| C: `Officer`, no role, no names, id 1 | `Officer #1001` | `Officer #1001` (no double space) | `Officer &7#1001&7` |
| A with `{rank} {name}` | n/a | `Officer Bob` | `&9Officer Bob&7` |

Plain `callsign(npc)` (logs, `CopListCommand`) is unchanged for cop A: `Officer Medic Bob #1592`.

Visible change: the default keeps the same characters, but rank and role now show their colours (ruling 1).
Owners who removed `%role%` from `Cops.Names.Format` to hide the role on the radio must now edit `Speaker_Name`
(`cops.yml` comment updated).

## Code

- `CopRadio.speakerName(AbstractNpc)`: the `Speaker_Name` template filled from the cop's parts, plus the line colour.
  Used for all radio-bound speakers and for `%unit%`/`%member%` in line bodies (`RadioVoice.callsign` delegates here).
- `CopRadio.callsign(AbstractNpc)` (static, plain): the default template with colours stripped. Symbol-stripping
  string parsing removed.
- `CopRadio.render`: a bold rank followed by literal text gets `&r` (`{rank}#{number}` gives `&1&lSWAT&r#1204&7`).
- `CopNpc.firstName` is set at spawn in `CopNpcFactory`. The nameplate surfaces the picked first name.
- `CopFieldCare` radio-bound member maps use `speakerName`. Logs and the list command stay on `callsign`.
- `CopRadioMessages.lines("Speaker_Name")` is a root key, so the Lines-regex parity tests are unaffected.
- Docs: `documentation/developer/cops-n-crooks.md` and `documentation/features/cops-n-crooks.md` describe the key.

## Tests and red proof

Red commit `73b1a94a` ("0.16.1 T-190: red tests"). Offline Maven over CopRadioTest, CopRadioMessagesTest and
CopFieldCareTest: 75 run, 10 failing, each for the expected reason:

- `CopRadioTest.speakerName_exampleA_coloursKeptLineColourResumes`: got `Officer Medic Bob #1592`.
- `CopRadioTest.speakerName_exampleB_boldRankAndRoleColour`: got `SWAT Marksman Tony #1204`.
- `CopRadioTest.speakerName_exampleC_emptyRoleAndName_noDoubleSpace`: got `Officer #1001`.
- `CopRadioTest.speakerName_ranknameTemplate`: template not read.
- `CopRadioTest.speakerName_noColourBeforeLine_noSuffix`: plain callsign returned.
- `CopRadioTest.sayAs_lineColourResumesAfterName`: sendMessage mismatch.
- `CopRadioTest.sayAs_bodyCallsignResumesLineColour`: sendMessage mismatch.
- `CopRadioTest.callsign_usesCopsOwnCallsignStripped` (flipped to parts-based `SWAT #1017`): got `Officer Bob #1017`.
- `CopFieldCareTest.medicMoving_namesPatientWithSpeakerName`: member argument mismatch.
- `CopRadioMessagesTest.speakerName_isARootKey_defaultAndShipped`: `Speaker_Name` returned `[]`.

Guards that passed before the change (kept): `callsign_exampleA_plainMatchesToday`, `callsign_plainHasNoColourCodes`,
`defaultFormat_visibleLineMatchesToday`.

Green: the string-parsing tests `callsign_dropsRoleSymbol` and `callsign_symbolRemovalIsAnchored` were removed.
Added in the worktree after the red commit: the `&r` bold-rank guard (test `speakerName_boldRank_resetBeforeLiteralText`,
red first as `expected <&1&lSWAT&r#1204&7> but was <&1&lSWAT#1204&7>`), `spanishFile_carriesItsOwnSpeakerName`, and the
Spanish key.

Final run (`mvn -o -pl gangland-features/cops-n-crooks -am test`): cops-n-crooks **1024 run, 0 failures, 0 errors,
0 skipped, BUILD SUCCESS**.

## Follow-ups

- Logs and `CopListCommand` use the default template, not the owner's `Speaker_Name` (D4). Injecting `CopRadio` into
  those callers would fix it, at more churn.
- The Spanish file replaces the English file as a whole, with no key-by-key fallback (its header comment says so).
  Any future key must be added to both files.
- No server smoke run in this lane. Owner should check one radio line in game after deploy.
- Not pushed.
