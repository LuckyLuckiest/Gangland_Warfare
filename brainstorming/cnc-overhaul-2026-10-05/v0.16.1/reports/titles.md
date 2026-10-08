# 0.16.1 lane: configurable, quieter wanted-level titles

Branch `cnc-0.16.1-titles`, base `2a2369f4`. Commits: `99748735` (red tests), `4301974f` (per-event cues), `4ba9a41f` (review fixes, docs and YAML comment only), plus the final commit `0.16.1: configurable, quieter wanted-level titles`. Not pushed.

## Sites

The only wanted-level title send is `WantedHudListener.announce`. It routes through `TitleCue.send`.

| Area | File |
|---|---|
| Send path (new) | `gangland-features/cops-n-crooks/.../copsncrooks/wanted/hud/TitleCue.java` |
| Event handling | `.../copsncrooks/listener/wanted/WantedHudListener.java` (`announce`, `onLevelChange`) |
| Parsing | `.../copsncrooks/wanted/config/ChaseConfig.java` (`hud`, `titleCue`) |
| Settings record | `.../copsncrooks/wanted/config/HudSettings.java` (`title` flag removed; `gain`, `lost`, `escaped` cues added) |
| Shipped YAML | `gangland-features/cops-n-crooks/src/main/resources/copsncrooks/wanted.yml` (`Wanted.Hud.Title`) |
| Messages | `WantedMessages.java` (`Key.TITLE` removed), `wanted_messages.yml` (`Hud.Title` removed) |

Out of scope, unchanged: detainment, cuffing, break-free, bribe, turf, and the death respawn title. Those read Messages contracts, not wanted config. Siren, boss bar, zone ring, compass, `WantedHud`, `StarCard` text, and the chat fallback are unchanged.

## YAML block (`copsncrooks/wanted.yml`)

Defaults as shipped in 0.16.1 (the code defaults match, see the parse test below):

```yaml
      # The title flashed on the screen when the wanted level changes. Enable (false = no title on any event)
      # is the master switch. Each event has its own Title and Subtitle. Placeholders: %stars% the star row,
      # %level% the new star count, %card% the star card line (empty while Star_Card is off). A blank Title
      # and a blank Subtitle send nothing. To blank a line write Title: "" or Subtitle: "" (a bare key with no
      # value reads as absent and takes the default). Gained and Lost show the star row in the subtitle, so the count is still
      # shown with Star_Card off. Fade_In, Stay and Fade_Out are in ticks (20 = 1 second).
      # Old look (before 0.16.1): Title "&c%stars%", Subtitle "%card%", Fade_In 5, Stay 40, Fade_Out 10 on every event.
      Title:
         # false = no title on any event
         Enable: true
         # A star gained
         Gain:
            Enable: true
            Title: ""
            Subtitle: "%stars% %card%"
            Fade_In: 5
            Stay: 20
            Fade_Out: 5
         # A star lost while still wanted
         Lost:
            Enable: true
            Title: ""
            Subtitle: "%stars% %card%"
            Fade_In: 5
            Stay: 20
            Fade_Out: 5
         # Wanted cleared by evasion or decay (the level reaches 0)
         Escaped:
            Enable: true
            Title: ""
            Subtitle: "%card%"
            Fade_In: 5
            Stay: 20
            Fade_Out: 5
```

Old look for an owner who wants it back: `Title: "&c%stars%"`, `Subtitle: "%card%"`, `Fade_In: 5`, `Stay: 40`, `Fade_Out: 10` on all three events.

### Deviation from the spec

The spec said Gain and Lost use `Subtitle: "%card%"`. The commit uses `"%stars% %card%"` and a separate `TitleCue.ESCAPED` constant (`%card%` only). The reason, from the commit message: with Star_Card off, `%card%` is blank and the star count would disappear. Escaped has level 0, so its star row would be empty. The owner should confirm this choice.

## Tests and red proof

Red state committed as `99748735`. Per the lane report, it ran 115 tests across WantedHudListenerTest, TitleCueTest, ChaseConfigTest, WantedMessagesTest, WantedHudTest and StarCardTest, with 17 failing for the right reason:

- `WantedHudListenerTest` (11): the title args (fades 5/40/10 and the `Key.TITLE` text) still differ from the flipped expectations. `gainCue_*`, `lostAndEscapedUseTheirOwnCue`, and `titleOn_starCardOff_sendsNothing` also fail for the right reason.
- `ChaseConfigTest` (4): `parse_overridesEveryBlock`, `parse_bundledFile_equalsDefault`, `hud_titleEvents_parsePerEvent`, `hud_masterTitleOff_disablesEveryEvent`.
- `TitleCueTest` (2): `send_enabled_substitutesPlaceholders_passesFades`, `send_subtitleOnly_sendsEmptyTitle`.

Regression guards that passed on the stub, by design: the chat fallback when a cue is disabled, the blank-cue no-send cases, `disabledCue_stillConsumesPendingPlan`, `disabledCue_siren_stillPlays`, `hud_absentTitleBlocks_useDefaults`, and the WantedMessages test after the switch to `Key.BAR_EVADED`.

I did not re-verify the red history in this run. The counts above come from the lane report.

## Counts (final, this run)

`mvn -q -o -pl gangland-features/cops-n-crooks -am test` exited 0.

- Module total: 100 suites, 1027 tests, 0 failures, 0 errors, 0 skipped.
- `TitleCueTest` 5, `WantedHudListenerTest` 22, `ChaseConfigTest` 57, `WantedMessagesTest` 5, `WantedHudTest` 14, `StarCardTest` 13.

## Follow-ups

1. **Open, needs in-game check:** an empty Title with a non-empty Subtitle is sent as subtitle-only (`sendTitle("", subtitle, ...)`). Verify on the 1.16.5 Test Server that vanilla shows the subtitle alone. The reviewer asked for no code change until that check is done.
2. Owner decision: confirm the Gain/Lost subtitle `"%stars% %card%"` and the separate Escaped cue (deviation above).
3. Owner decision: the chat fallback when a cue is disabled and Star_Card is on is kept by default. Turning Star_Card off silences it.
4. Existing servers keep the old `Hud.Title` keys in `wanted.yml`, which are now ignored. The live Test Server YAML is not regenerated, so those servers get the code defaults on upgrade. Put this in the changelog.
5. Other title sites (detainment, cuffing, break-free, bribe, turf, respawn) are not routed through `TitleCue`. Needs an owner decision.
6. `%lost%` is not a placeholder. Add it when a cue needs the number in the subtitle.
