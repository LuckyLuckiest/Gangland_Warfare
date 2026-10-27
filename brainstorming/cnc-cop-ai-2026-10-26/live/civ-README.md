# cop-ai-civ: civilian witness / 911 acceptance (0.21.1, civ merged)

Sandbox only: server `E:/Programming/java/wt/_programme/servers/cop-ai-civ`, port 25614, a fresh clone of
`servers/base` on every run. The owner's live Test Server is never touched. Design: `../CIVILIAN-911-DESIGN.md`. The
owner answered Q1-Q10 on 2026-10-26: Kill_Civilian is witness-gated, the view cone is 180 degrees, loud crimes turn
civilians, an unarmed right-click within 4 blocks stops a call, and a stopped caller has a scared cooldown.

## Run

```bash
cd "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-cop-ai-2026-10-26/live"
node gen-civ.js                                   # scen/civ-*.json are generated: rerun after editing gen-civ.js
bash run-civ.sh civ-911 civ-<round>-<n>           # TAG MUST BE NEW: an existing runs/cop-ai-civ--<tag> is refused (exit 4)
bash run-civ.sh civ-boot civ-<round>-boot-<n>     # boot check + placement dry run (~2 min)
```

- Output: `E:/Programming/java/wt/_programme/runs/cop-ai-civ--<tag>/`. It holds `server.log` (with `>>> STEP n`
  markers), `chat.txt`, `summary.txt`, `steps.json` and `deployed.md5`.
- `chat.txt` records every bot's chat. It also records action bars as `[<bot>/game_info]`, titles as
  `[<bot>/title] [subtitle] ...` and boss bars.
- `rc=1` means a setup expect failed: a spawn reply was missing, or W_D was still fleeing after 30 s. It never means a
  behaviour failed. Read the behaviour verdicts with the criteria below.
- The full `civ-911` scenario takes about 6 minutes after boot. Do not run it in parallel with another cop-ai-civ run,
  because both use the same server dir and port.

## Deployed

| What | From |
|---|---|
| Gangland core and 10 modules (REV 0.21.1) | `E:/Programming/java/wt/gangland-0.21.1/target` (branch 0.21.1, which merged 0.21.1-civ at f52cda0cd; since ACC7, 2026-10-27). Env `GL` overrides it (the pre-merge runs used `wt/gl-0.21.1-civ/target`). `run-civ.sh` copies whatever jars are in that directory. |
| Keystone 1.15.4 | `E:/Programming/java/wt/keystone-1.15.4/keystone-plugin/target/Keystone-1.15.4.jar` |
| Bartizan 0.6.0, Citizens 2.0.43, Paper 1.21.11 | As the other cop-ai rows (`prep.sh`) |

## Overrides

Everything not listed here uses the shipped defaults.

- From `prep.sh`:
  - flat world, view-distance 16;
  - `Debug.Enabled: true`;
  - respawn on, with a 3 s delay.
- From `prep-civ.sh`:
  - `Debug.Modules: ["Cops N Crooks", "Gangland", "Gangland Civilians"]`;
  - `npc/civilians.yml` is extracted from the civilians jar with one edit, `Types.pedestrian.AI.Wander.Enabled: false`.
    This keeps a placed civilian where the scenario puts it. Its idle head turns still run.
- Witness keys, all shipped values: `View_Angle 180`, `Call_Seconds 8`, `Notice_Seconds 1`, `Intimidate_Range 4`,
  `Scared_Cooldown_Seconds 60`, `Loud_Shot_Seconds 3`, `Loud_Weapon_Types [GUN, THROWABLE]`, `Range 24`.
- `wanted.yml` `Witnesses.Crimes`: `Car_Theft`, `Assault_Civilian`, `Kill_Civilian`.
- Scenario world rules: no mob, monster or wandering-trader spawns, time frozen at 6000 (noon), clear weather.
- Every bot gets Resistance V, Saturation and Strength 101 (amplifier 100), so a single fist hit kills a pedestrian.
- There is no cop station or spawner. When a call lands, the star creates the cop group with no unit yet. That is the
  T-345 case.

## Layout and conventions

Each row has its own bot and its own X, 600 blocks apart, so no row's cops or civilians reach another row. The bot
stands at `(X+.5, .5)` and the victim V at `(X+2.5, .5)`, both at `y=-60`.

**Facing is set by who stands near W, not by a teleport yaw.** A teleport yaw does not hold, because the civilian AI
ticks every server tick (see the finding below). The idle look-around re-aims every 15 to 35 ticks, and it always picks
the nearest player inside a 12-block box first:

| Case | W's position | Why it faces that way |
|---|---|---|
| **front** (A, D, E2) | `(X+.5, 8.5)`, 8 blocks south of the bot | The bot is the nearest player, so W keeps facing it (yaw about 180). |
| **behind** (B, C) | `(X+.5, 14.5)`, 14 blocks south of the bot | Bot `Decoy` stands at `(X+.5, 20.5)`, 6 blocks further south. Decoy is the only player in W's box, so W keeps facing it (yaw about 0) and the bot is at W's back. |

Facing is set both ways. Every teleport also sets the yaw: 0 for a W at z=14.5, 180 otherwise. If the AI tick is
fixed to once a second, the idle look re-aims only every 15 to 35 s, and the teleport yaw then holds. Today the
nearest-player look re-asserts the same yaw about every second.

The `Rotation` probe right before the `<row>-CRIME` marker is the yaw the cone saw.

**Yaw convention:** 0 = south, 180 = north, -90 = east, 90 = west. Minecraft does not wrap the yaw, so compare it
modulo 360 (251.8 is the same as -108.2). The `<npc> has the following entity data: [yaw, pitch]`
line follows the `>>> STEP n {"console":"data get entity @e[tag=W_x,...] Rotation"}` marker. Every civilian is named
`Pedestrian`, so the STEP marker is what identifies it.

**The crime** is always Kill_Civilian by fist, in one hit. It is worth 100 heat, which is star 1 once a call lands.
Assault_Civilian (30 heat) would never reach a star.

**Markers:** `say CNCMARK:CIV:<row>-<label>` appears in `server.log` and as `[Server] CNCMARK:...` in `chat.txt`. A row's
window runs from its `<row>-START` marker to the next row's `-START`.

**Debug lines** (Gangland Civilians and Cops N Crooks):
- `WITNESS <bot> crime=Kill_Civilian callers=<0|1>`
- `CALL_IN <bot> crime=... caller=...`
- `CALL_CANCEL <bot> crime=... reason=<hit|scared|intimidated|lost|killed|logout>`
- `Transitioning civilian <name>-<id> from <A> state to <B> state.`
- `DISPATCH <bot>`, `UNIT <bot>`

**Text the bot sees** (shipped YAML):

| Line | Shipped text |
|---|---|
| Witness_Start | `[911] Pedestrian: Hey! I saw that! I'm calling the cops!` |
| Countdown action bar | `911 Pedestrian is calling - <n>s` |
| Witness_Scared | `[911] Pedestrian: Okay, okay! I'm hanging up!` |
| Stopped action bar | `Call stopped.` |
| Witness_Landed | `[911] Pedestrian: Yes, police? He's right here, near here!` (Witness_Unknown_Place, because the sandbox has no named places) |
| Caller_911 | `[DISPATCH] 911 caller reports Killing a civilian near the area. Suspect: unknown suspect.` (the cop-side place fallback; no cop has seen the suspect yet) |
| Star card | `Killing a civilian (reported by a witness): <tier> inbound, ...` |
| Star subtitle | `[subtitle] ★☆☆☆☆` |
| `/glw wanted` | `GLW >> Wanted Status:`, followed by `☆☆☆☆☆` (not wanted) or `★☆☆☆☆` (level 1) |

## Pass criteria

Every run must also show:
- `Runtime modules: 10 loaded, 0 fault(s)`;
- `summary.txt` `== EXCEPTIONS (0 distinct) ==`;
- the only ERROR is the base-server noise `No key layers in MapLike[{}]`;
- every COPS probe before a row's `-CRIME` marker prints no `has the following entity data`, so no cop exists.

A row's window runs from its `<row>-START` marker to the next row's `-START`. Yaws are compared modulo 360.

### A. Silent crime in front of a civilian (bot `Silent`, X=0, W_A front)

All of the following, in the A window:

1. The `W_A Rotation` just before `A-CRIME` is about 180 (within 30), so W_A faces the bot.
2. `WITNESS Silent crime=Kill_Civilian callers=1` appears within 1 s of `A-CRIME`.
3. `[Silent]` chat shows `[911] Pedestrian: Hey! I saw that! I'm calling the cops!`.
4. Notice, at `A-NOTICE`:
   - `W_A Rotation` is about 180, so it looks at the bot;
   - `equipment` shows `minecraft:clock` in the mainhand;
   - `Glowing` is `1b`.
5. Dialling, at `A-DIAL`: `W_A Rotation` is about 0 (within 15), so it has turned away. It still holds the clock and
   still glows.
6. The countdown action bar shows `[Silent/game_info] 911 Pedestrian is calling - <n>s`. The values only go down: it
   starts at 8 (or 7), ends at 1, and has no gaps of more than one second.
7. `CALL_IN Silent crime=Kill_Civilian` comes 8 to 9.5 s after `WITNESS`. Just before it, chat shows `[911]
   Pedestrian: Yes, police? He's right here, near here!`.
8. T-345, required: `[DISPATCH] 911 caller reports Killing a civilian near ...` arrives in `[Silent]` chat. No cop
   exists when it arrives: there is no `UNIT Silent` line in `server.log` before the dispatch line, and the `A-PRE`
   COPS probe was empty.
9. The star:
   - `[Silent/title] [subtitle] ★☆☆☆☆`;
   - a chat line containing `Killing a civilian (reported by a witness)`;
   - after `A-LANDED`, the `/glw wanted` reply is followed by `★☆☆☆☆`.
10. At `A-LANDED`, W_A's `equipment` mainhand is no longer the clock, and `Glowing` is `0b`.

### B. Silent crime behind a civilian's back (bot `Backer`, X=600, W_B faces Decoy)

1. The `W_B Rotation` just before `B-CRIME` is about 0 (within 30). The bot is behind W_B, outside the 180-degree cone.
2. `WITNESS Backer crime=Kill_Civilian callers=0`.
3. None of the following appear in `[Backer]` chat in the B window: `[911]`, `is calling`, `[DISPATCH]`,
   `(reported by a witness)`, `[subtitle] ★`.
4. There is no `CALL_IN Backer` line, and `/glw wanted` after `B-AFTER` is followed by `☆☆☆☆☆`.
5. `W_B Rotation` at `B-NOTICE` and `B-AFTER` is still about 0: it never turned to the bot.

### C. Loud crime (pistol shot) behind a civilian's back (bot `Loud`, X=1200, same geometry as B)

1. Precondition, the shot fired: `ammo-left` in the `SelectedItem` probe after `C-SHOT` is one lower than in the probe
   before it. If it is unchanged, the row is void (a harness problem), not FAIL.
2. The `W_C Rotation` just before `C-SHOT` is about 0 (within 30): its back is to the bot, as in B.
3. `WITNESS Loud crime=Kill_Civilian callers=1`. B and C have the same geometry; only the shot differs.
4. The turn: `W_C Rotation` at `C-NOTICE` is about 180. It turned from facing Decoy to the noise and the criminal.
5. As A, items 3 and 5 to 10, with `Loud`:
   - Witness_Start;
   - the turn-away at `C-DIAL`;
   - the countdown;
   - Landed and `CALL_IN`;
   - the `[DISPATCH] 911 caller reports` line;
   - star 1 with `(reported by a witness)`;
   - the phone put away.

### D. Unarmed right-click stops the call, then the scared cooldown (bot `Grabber`, X=1800, W_D front)

1. The call starts as A, items 1 to 4.
2. `D-GRAB`: the bot holds nothing (it was never given an item). It is teleported 2 blocks from W_D and right-clicks it.
3. Within 1 s of `D-GRAB`, all of the following:
   - `CALL_CANCEL Grabber crime=Kill_Civilian reason=intimidated`;
   - chat `[911] Pedestrian: Okay, okay! I'm hanging up!`;
   - action bar `[Grabber/game_info] Call stopped.`;
   - at `D-STOPPED`, W_D has no clock in its mainhand and `Glowing` is `0b`.
4. No star from that call:
   - there is no `CALL_IN Grabber` before `D-CONTROL-CRIME`;
   - there is no `[DISPATCH]` or `(reported by a witness)` before `D-CONTROL-CRIME`;
   - `/glw wanted` at `D-STOPPED` and at `D-REPEAT-AFTER` is followed by `☆☆☆☆☆`.
5. W_D settles before the repeat:
   - `Transitioning civilian <name>-<id> from CALLING state to FLEEING state.`;
   - then `... from FLEEING state to IDLE state.` for the same `<name>-<id>`;
   - both before `D-SETTLED` (the scenario waits for the second line, which is a setup expect).
   W_D is therefore IDLE, not FLEEING, at the repeat, and only the cooldown can stop it calling.
6. Repeat inside the cooldown:
   - the `W_D Rotation` just before `D-REPEAT-CRIME` is about 180, so it faces the bot;
   - `WITNESS Grabber crime=Kill_Civilian callers=0` after `D-REPEAT-CRIME`;
   - there is no `[911]` line between `D-REPEAT-CRIME` and `D-CONTROL-CRIME`;
   - W_D shows no clock and no glow at `D-REPEAT-NOTICE`.
7. Control after the cooldown:
   - after `D-CONTROL-CRIME`: `WITNESS Grabber crime=Kill_Civilian callers=1`, Witness_Start, `CALL_IN Grabber`, star 1
     and `(reported by a witness)`;
   - this is valid only when `D-CONTROL-CRIME` comes at least 60 s after `D-GRAB` in `server.log` time.
   If that timing does not hold, the control is void (harness), not FAIL.

### E. Kill_Civilian with no witness, then with one (bot `Killer`, X=2400)

1. E1 setup: W_E is parked at `(X+40.5, 14.5)`, more than 24 blocks from the crime. The `E1-PRE` `Pos` probe confirms it.
   V2_E is parked at `(X+40.5, .5)`.
2. E1 result:
   - `WITNESS Killer crime=Kill_Civilian callers=0`;
   - between `E1-CRIME` and `E2-PRE`: no `[911]`, no `[DISPATCH]`, no star subtitle and no `CALL_IN Killer`;
   - the `/glw wanted` reply after `E1-AFTER` is followed by `☆☆☆☆☆`.
   So the unseen kill gives no star (Q1).
3. E2: W_E is moved in front. The rest is as A, items 1 to 10, with `Killer`:
   - `callers=1`;
   - Witness_Start;
   - look-at then turn-away;
   - the countdown;
   - `CALL_IN`;
   - `[DISPATCH] 911 caller reports Killing a civilian`;
   - star 1 with `Killing a civilian (reported by a witness)`.

## Boot check (`civ-boot`)

Pass on all four:

1. The server starts cleanly:
   - `Done (` and `Runtime modules: 10 loaded, 0 fault(s)`;
   - versions: Keystone 1.15.4, Gangland_Warfare 0.21.1, Bartizan 0.6.0, Citizens 2.0.43;
   - 0 exceptions;
   - `[Gangland Civilians.CivilianService] CivilianService initialized` at DEBUG, so the civilians debug logging is on.
2. Placement holds. `P_F` and `P_B` `Pos` are the same at `BOOT-PLACED` and at `BOOT-PLACED-6S` (wander is off and the
   teleport holds).
3. Facing works at both markers:
   - `P_F` (front case, Placer 8 blocks north of it) has a Rotation of about 180;
   - `P_B` (behind case, Decoy 6 blocks south of it) has a Rotation of about 0.
4. `BOOT-SHOT`: `ammo-left` goes from 12 to 11.

`civ1-boot` (2026-10-26, on the first layout) passed items 1, 2 and 4. Its teleport yaws did not hold (P_W was placed
at 135 and read 91.4, then 251.8 six seconds later), which is why the layout above exists.

## Finding while building this (not in the docket, needs a T- id)

**P2: civilian AI ticks every server tick, not every `AI_Tick_Rate` (20) ticks.**

- **Where:** `CivilianService.java:157`, `new RepeatingTimer(plugin, tickRate, 0, ...)`. Keystone's 4-argument
  constructor is `(plugin, delay, period, task)`, so the call gives delay=20 and period=0. Bukkit treats period 0 as 1.
  The `checkTimer` at `:161` has the same swap (`Check_Interval` 100). This has been in place since 1602e41b1
  (2026-09-09) and is on master.
- **Effects:**
  - The idle look-around (15 to 35 AI ticks) re-aims about every second.
  - The witness scare poll and the CALLING behaviour run 20 times a second, not once a second. The design doc and the
    `Scare_Angle` YAML comment say "once per AI tick (1 s)".
  - Everything else counted in civilian AI ticks runs 20 times faster than configured.
- **Seen in:** `runs/cop-ai-civ--civ1-boot`, where P_W's yaw went 135, then 91.4, then 251.8 within 8 s while it stood
  still.

## Env checks done (2026-10-26, gl-0.21.1-civ target jars built 20:34-20:38, branch tip b8ca509f1)

These are previews for the judge, not verdicts. The orchestrator runs the acceptance with a new tag.

- **`runs/cop-ai-civ--civ2-boot`** and **`civ3-boot`** (civ3 with the yaw on every teleport; both rc=0, 71/71 steps,
  0 exceptions): every boot-check item passes.
  - P_F reads 180.0 at both probes, 6 s apart; P_B reads 0.0 at both.
  - The positions are unchanged between the two probes.
  - `ammo-left` goes from 12 to 11.
- **`runs/cop-ai-civ--civ2-envcheck`** (`civ-911`; rc=0, 406/406 steps, 0 exceptions; the only ERROR is the 3x
  MapLike noise; it ran before the yaw was put back on the teleport). Every row behaved as its criteria describe:
  - **A:** W_A went 180 at notice, then 0 at dial. The countdown ran 8s to 1s, and `CALL_IN` came 8 s after `WITNESS`.
    The `[DISPATCH] 911 caller reports ...` line arrived 1 s before the first `UNIT Silent`, together with the subtitle
    `★☆☆☆☆` and `Killing a civilian (reported by a witness): Officer inbound, ...`.
  - **B:** `callers=0`, and W_B stayed at 0.0.
  - **C:** `ammo-left` went from 12 to 11, W_C went from 0 to 180 to 0, and `callers=1` led to the full flow.
  - **D:** the call was stopped by intimidation at the 4 s mark, with `Call stopped.` W_D settled 3 s later. The
    repeat 11 s after the stop gave `callers=0`. The control 82 s after the stop gave `callers=1` and the full flow.
  - **E:** E1 gave `callers=0` and `☆☆☆☆☆`; E2 gave the full flow.
- Run time is about 6 minutes after a 20 s boot.
