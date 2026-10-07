# smoke.py -- console-only smoke-test harness

Drives the Gangland Warfare Paper test server (`E:\Documents\Minecraft\Test Server`) purely through
its console -- stdin/stdout on `ServerStartDebug.bat` -- deploys a chosen combination of runtime
module jars, sends a scripted command plan, watches the log for module-load lines / faults / errors,
and writes a Markdown + JSON report per run. **Python 3 standard library only.**

This directory (`smoke/`) is self-contained. The harness never builds the plugin, never runs `mvn`,
never touches git, and (outside of `--deploy`/`--keystone`/`--restore`, which only ever touch the
*server's* `plugins/` folder) never writes outside `smoke/reports/`.

## Origin of the matrix

The task that produced this harness pointed at `..\README.md` (i.e.
`brainstorming\bartizan-split-2026-09-08\README.md`) as the source of the "Smoke matrix" table. That
file did not exist yet at authoring time -- this harness's own `smoke/` directory was the first thing
created under that path -- so the 8 rows (S1-S8) were first encoded directly from the harness task's
own description. Partway through building this harness, a `../README.md` appeared (written by a
sibling agent in the same coordinated "Bartizan Wave" effort this harness belongs to -- see its
`## Status board`, which lists `A · smoke harness | in progress | agent H1`, i.e. this task). Its
`## Smoke matrix (phase A, and again in phase D)` section is now the authoritative one, and
`scenarios.json` has been reconciled against it:

- Same 8 rows, same module sets per row.
- The table's last row -- "Every row: `stop` -> `Disabling`, `onDisabled` per module, no classloader
  errors, no fault spam" -- is folded into each row's own `expect.must_contain` (`Disabling` and
  `onDisabled` added to every row that actually loads at least one module; S1 and S6 load none, so
  those two strings are left off rather than asserting something that would vacuously never be
  produced).
- S1 additionally checks for a generic `Runtime modules:` line (the table says "boots, `Runtime
  modules: 0 loaded, 0 fault(s)`" -- the exact wording after the count, `fault(s)` vs. `failed` etc.,
  was not independently confirmed against real Keystone module-loader source in this session, so only
  the stable prefix is asserted rather than a guessed full phrase).
- Two items from the authoritative table are **deliberately not** encoded as automated `expect`
  checks, because a console-line substring match cannot honestly represent them: S4's "detainment
  table migrates" (a database-level fact, invisible in console output) and S1's "`/glw help` lists only
  core commands" (a count comparison against a moving target, not a fixed string). Both need a human
  glance at the row's report (`reports/<stamp>-S1.md` / `-S4.md`) rather than a boolean the harness can
  compute on its own -- see Limitations.

The "Bartizan Wave" board also documents six findings from a prior smoke run (T-12..T-18, filed in the
bug docket) that two sibling agents (K1 for Keystone, G1 for Gangland) are fixing in parallel with this
harness being built. `scenarios.json`'s `no_errors_except` intentionally does **not** pre-allow any of
those -- Phase A's gate (GA) is "every matrix row boots with 0 Gangland/Keystone ERRORs", i.e. the
fixes are expected to land before the real matrix run, not be silently tolerated by the harness.

## Quick start

```
python smoke.py --list                              # print all scenario rows
python smoke.py --dry-run --deploy --rows S4         # print the plan, touch nothing, start nothing
python smoke.py --fake --rows S1,S2                  # exercise the harness with no real server at all
python smoke.py --rows S1,S2 --deploy --keystone      # deploy + run against the REAL server
python smoke.py --rows S1..S8 --deploy --restore      # (see note below -- ranges aren't supported)
python smoke.py --rows S1,S2,S3,S4,S5,S6,S7,S8 --deploy --restore   # full matrix, restore user jars after
```

`--rows` takes a comma-separated list of scenario ids (`S1,S2,...`); there is no range syntax.

## CLI flags

| Flag | Effect |
|---|---|
| `--rows S1,S2,...` | Which scenario rows to run. Required unless `--list` or `--restore`-only. |
| `--deploy` | Copy the freshly built core jar (`target/gangland_warfare-*.jar`) and the row's module jars (`target/modules/*.jar`) into the server's `plugins/` tree before starting. Without it, whatever jars are already in `plugins/` are used as-is. |
| `--keystone` | Also copy the freshly built `Keystone-*.jar` from the sibling Keystone repo's `keystone-plugin/target/`. |
| `--restore` | After the matrix finishes (or on its own, with no `--rows`), move any module jars that were parked aside at the start of this invocation back into `plugins/Gangland_Warfare/modules/`. |
| `--dry-run` | Print the deploy plan and the command plan for each selected row. **Never starts a process and never copies/deletes a file.** |
| `--fake` | Drive an embedded fake server instead of the real Paper server (see below). Ignores `--deploy`/`--keystone` (there is nothing real to deploy to). |
| `--list` | Print every scenario id, its module set and title, then exit. |
| `--scenarios PATH` | Use a different scenario file (default: `scenarios.json` next to `smoke.py`). |
| `--reports-dir PATH` | Write reports somewhere other than `smoke/reports/`. |

Exit code is `0` if every run in the matrix PASSed, `1` if any FAILed (or on a `--list`/`--dry-run`-only
invocation it is always `0`).

## What one run actually does

1. **Preflight** (real mode only -- skipped for `--fake` and `--dry-run`): refuses to start if TCP
   port `5005` (JDWP) or `25565` (Minecraft) is already bound, or if a `java.exe` process is found
   whose command line mentions `paper-1.21.11.jar`. Detection tries `wmic` first, falls back to
   PowerShell `Get-CimInstance Win32_Process`, falls back to a bare `tasklist` presence check (which,
   lacking command-line visibility, is reported as an *unconfirmed possible* conflict rather than a
   hard failure signal misrepresented as certain).
2. **Deploy** (only with `--deploy`/`--keystone`): see "Deploy semantics" below.
3. **Start**: `cmd /c <ServerStartDebug.bat>` with `stdin=PIPE, stdout=PIPE, stderr=STDOUT, text=False`,
   a background daemon thread decodes each line as UTF-8 (`errors="replace"`) and appends it to an
   in-memory list. See "Windows quirks" for why the `.bat` is invoked by its resolved path rather than
   the bare filename literally shown in the task brief.
4. **Wait for boot**: polls for a line matching `Done \(` up to `boot_timeout_seconds` (default 180s).
   On timeout, the whole process tree is killed and the row is marked FAIL.
5. **Commands**: sends each command in the row's plan (`\n`-terminated bytes on stdin), waiting
   `command_delay_seconds` (default 2s) between each; the output lines that arrived in that window are
   captured as that command's transcript.
6. **Stop**: sends `stop`, then waits for the console to go *quiet* (no new line for 3s) or for the
   `Press any key` pattern to show up as a complete line -- whichever comes first -- releases the
   trailing `pause` with a bare newline, and waits for `cmd.exe` itself to exit. One retry newline is
   sent if the first release didn't lead to exit within budget. If `cmd.exe` still hasn't exited once
   `stop_timeout_seconds` (default 120s) is spent, the whole tree is killed with `taskkill /F /T /PID`.
   See "Why not just wait for the prompt" below -- this is not a stylistic choice, it avoids a real
   deadlock.
7. **Log copy**: `logs/latest.log` is copied to `reports/<stamp>-<id>.log` immediately, before the next
   scenario's start would gzip it away.
8. **Parse + evaluate**: every log line containing `ERROR`, `WARN`, `Exception`, `Caused by`,
   `[Keystone`, `[Gangland`, `Loaded module`, `Runtime modules:`, `module.`, `Disabling`, `enabled`, or
   `onDisabled` is kept as an "interesting" line; `Loaded module <id>` lines are extracted; `ERROR`
   lines are deduplicated by signature (timestamp bracket(s) stripped) and counted. The row's `expect`
   block (see below) is checked against all of this.
9. **Report**: `reports/<stamp>-<id>.md` (verdict, expectations table, module lines, full command
   transcripts, distinct ERROR signatures, first 40 ERROR/WARN lines with their first
   `at org.luckyraven...` stack frame if one follows within 15 lines, and the copied log's path), plus
   a shared `reports/<stamp>-summary.md` and `reports/<stamp>-summary.json` across the whole matrix.

## Deploy semantics (`--deploy`)

- The core jar: newest match of `target/gangland_warfare-*.jar` under the repo replaces whatever core
  jar(s) currently sit in the server's `plugins/` (any revision -- old ones are deleted first).
- `--keystone`: same idea for `Keystone-*.jar`, sourced from
  `E:\Programming\java\Keystone\keystone-plugin\target\`.
- Modules: **once per whole `smoke.py` invocation** (not per row), whatever jars are currently sitting
  in `plugins/Gangland_Warfare/modules/` are moved into
  `plugins/Gangland_Warfare/modules/.harness-parked/` (a marker file records this happened, so a
  second row in the same run does not try to park the harness's own jars). Then, before *every* row,
  the harness clears `modules/` (never touching `.harness-parked/`) and copies in exactly that row's
  module jars, resolved by prefix (`mail`->`gangland-mail-*.jar`, `cops`->`cops-n-crooks-*.jar`,
  `gadget`->`gangland-gadget-*.jar`, `turf`->`gangland-turf-*.jar`, `weapon`->`gangland-weapon-*.jar`)
  from `target/modules/`.
- Row S7 additionally builds a Host_Api-mismatched copy of the weapon jar with **pure `zipfile`** (no
  shell, no external tools): it reads `module.yml` out of the source jar, rewrites the `Host_Api:`
  line's value in place, and re-zips every other entry byte-for-byte into a new jar named
  `gangland-weapon-hostapi07.jar` inside `modules/`, alongside the untouched original.
- `--restore` moves whatever is in `.harness-parked/` back into `modules/` (after clearing out
  whatever the harness itself last placed there) and removes the parking marker/directory. Run it
  after the last row of a matrix, or on its own (`python smoke.py --restore`, no `--rows`) if a
  previous invocation was interrupted before it could restore.
- The harness **never** touches `plugins/Gangland_Warfare/settings.yml`, the world folders, or the
  database file.

`--dry-run` runs the exact same planning code path (`compute_deploy_plan`) but only prints what it
found and where it would go -- it does not call `shutil.copy2`, does not call `Popen`, and does not
touch `.harness-parked/`.

## Scenario file format (`scenarios.json`)

```jsonc
{
  "paths": { "server_dir": "...", "repo_dir": "...", "keystone_jar_dir": "...", "keystone_jar_pattern": "Keystone-*.jar" },
  "deploy": { "core_jar_glob": "target/gangland_warfare-*.jar", "modules_dir": "target/modules",
              "server_plugins_dir": "plugins", "server_core_jar_glob_relative": "gangland_warfare-*.jar" },
  "module_jar_prefix": { "mail": "gangland-mail", "cops": "cops-n-crooks", "civilians": "gangland-civilians", "npcshops": "gangland-npc-shops", ... },
  "legacy_module_jar_prefix": { "weapon": "gangland-weapon" },  // T-O9: only S1-S8 ("legacy": true rows) still resolve this id
  "module_probe_commands": { "civilians": "glw civilian list", "npcshops": "glw trader", ... },
  "default_commands": ["glw", "glw help", "glw help 2", "glw help 3", "glw modules"],
  "command_delay_seconds": 2, "boot_timeout_seconds": 180, "stop_timeout_seconds": 120,
  "scenarios": [
    {
      "id": "D5", "title": "...", "legacy": false,  // omit "legacy" (or set false) for a current-topology row; true for S1-S8
      "modules": ["mail", "turf", "civilians", "cops", "gadget", "npcshops"],  // which module ids get deployed (and probed)
      "plugins": ["Bartizan"],                    // T-O9: extra jars to stage into plugins/ (Bartizan is the only one this harness builds itself)
      "remove_plugins": ["Citizens"],              // T-O9: plugin names to actively park/remove for this row (D6, D7; also "Gangland_Warfare" for D0)
      "commands": null,                          // null = default_commands + one probe per module + stop
      "pre_commands": [{"wait_before": 10, "command": "glw reload"}],   // optional, sent after boot, before the main plan (S8, D8)
      "extra_module_jars": [{"source_module": "mail", "host_api_override": "0.8", "name_suffix": "hostapi08"}],  // optional (S7, D9)
      "expect": {
        "loaded_modules": ["mail", "turf", "civilians", "copsncrooks", "gadget", "npcshops"],  // exact set of `Loaded module <id>` lines expected
        "faults": ["module.dependency.missing"], // substrings that MUST appear somewhere in the log
        "must_contain": ["Done ("],              // substrings that MUST appear somewhere in the log
        "must_not_contain": ["Failed to create a backup"],  // T-O9: substrings that must NOT appear anywhere in the log
        "no_errors_except": ["\\[FakeSmoke\\]"]  // every ERROR line must match at least one of these regexes
      }
    }
  ]
}
```

All five `expect` keys are optional and independent; a row can declare any subset. `faults` and
`must_contain` are functionally identical (substring-must-appear) -- they are kept as two separate
keys only because the task described them as two separate concepts ("faults" vs. "always-true
sanity checks" like `Done (`); nothing stops a row from putting a fault string in `must_contain` or
vice versa. `must_not_contain` is the negative counterpart (substring-must-NOT-appear); a row cannot
express "this substring occurs exactly once" (e.g. D8's "no second `Item vocabularies installed:`
line") -- that needs a count check `evaluate_expect()` does not implement, called out in Limitations
below the same way S1's uncounted `/glw help` check always was.

### The seeded rows: legacy S1-S8 (0.8.4 topology) + the phase-D D0-D9 matrix (0.9.0 topology)

S1-S8 target the pre-Bartizan-split five-module topology (`weapon` still a Gangland module, no
Bartizan, no civilians/npcshops split) and are marked `"legacy": true` in `scenarios.json` --
`--list` labels them `[legacy]`. They stay dry-run-verifiable (`legacy_module_jar_prefix` keeps
`weapon` resolvable for them) but are not maintained against the current topology; prefer the D-rows.

| Row | Modules | What it's checking |
|---|---|---|
| S1 | (none) | Core boots clean with zero runtime modules |
| S2 | weapon | A single module loads |
| S3 | weapon, gadget | Two modules with gadget's `Depends: weapon` |
| S4 | turf, weapon, cops | Three modules, cops' `Depends: [turf, weapon]` |
| S5 | mail, cops, gadget, turf, weapon | All five, full dependency graph |
| S6 | cops (turf omitted) | Missing dependency -> expects `module.dependency.missing` fault |
| S7 | weapon + a Host_Api=0.7 copy | Incompatible module rejected, original still loads |
| S8 | all five + `glw reload` 10s after boot | Reload path doesn't re-explode the module graph |

D0-D9 (added by T-O9, `brainstorming/bartizan-split-2026-09-08/gangland-0.9.0.md` §G6 /
`REVIEW-consistency.md`'s "Smoke rows for phase D") target the shipped 0.9.0 topology: six Gangland
modules (`mail`, `turf`, `civilians`, `cops` → id `copsncrooks`, `gadget`, `npcshops`) plus the
standalone **Bartizan** plugin, staged/removed per row via the new `"plugins"`/`"remove_plugins"`
scenario keys instead of hand-editing the server between runs.

| Row | Modules | Also in `plugins/` | What it's checking |
|---|---|---|---|
| D0 | (none) | Bartizan, **no Gangland core** | Bartizan boots standalone |
| D1 | (none) | Bartizan, Citizens | Empty module set, Gangland core alone |
| D2 | civilians | Bartizan, Citizens | A single new-split module loads |
| D3 | turf | Bartizan, Citizens | Turf's new `Depends: [civilians]` -> `module.dependency.missing` |
| D4 | civilians, turf, cops | Bartizan, Citizens | `/glw turf powerupnpc` resolves without a `CommandContribution` |
| D5 | all six | Bartizan, Citizens | Full six-module graph, 0 faults |
| D6 | all six | Bartizan **removed** | `Plugins: [Bartizan]` modules skip with `module.plugin.missing` |
| D7 | all six | Bartizan, Citizens **removed** | Every NPC-owning module reports `npc.citizens.missing` once, still loads |
| D8 | all six | Bartizan, Citizens | `glw reload` doesn't re-explode the module graph |
| D9 | all six + a Host_Api=0.8 copy of mail | Bartizan, Citizens | Incompatible module rejected, the other six load |

See `gangland-0.9.0.md`'s G6 table for the exact `must_contain`/fault-id contract each row asserts.

## Reading a report

Each `reports/<stamp>-<id>.md` has, top to bottom: a one-line **Verdict** (PASS/FAIL), boot/stop
booleans, the deploy summary (what was actually copied, or "no --deploy given"), an **Expectations**
table (one row per `expect` key, PASS/FAIL + a detail string -- e.g. what was missing), the
`Loaded module` ids actually seen, every command's transcript (`>>> command` followed by the log
lines that arrived in the following `command_delay_seconds` window), the count and list of distinct
ERROR signatures, the first 40 ERROR/WARN lines with their first `org.luckyraven` stack frame when one
follows within 15 lines, and a collapsed `<details>` block with every "interesting" log line for deep
digging. The full raw log for that run sits alongside it as `reports/<stamp>-<id>.log`.
`reports/<stamp>-summary.md`/`.json` are the one-row-per-scenario roll-up across the whole invocation.

## Windows quirks handled (and why)

- **`[Cubed-GTA recoded]` in the repo path.** `glob.glob()` on a plain path *string* treats `[...]` as
  a character class, so a naive `glob.glob(str(repo_dir) + "/target/*.jar")` would silently fail to
  match anything. The fix used throughout: always call `pathlib.Path(base_dir).glob(pattern)` --
  `base_dir` is used as a literal directory (never re-parsed as a pattern), only the `pattern` argument
  is glob-syntax. See `glob_one()`/`glob_all()` in `smoke.py`. The same bracket problem bit
  `Get-ChildItem` during development of this harness (`-LiteralPath` was required) -- a reminder that
  this is a real, not theoretical, hazard on this machine.
- **`cmd /c <bare relative filename>.bat` cannot find the file, non-interactively.** Confirmed by a
  standalone repro during development: `cmd /c ServerStartDebug.bat` with `cwd` correctly set fails
  with *"'ServerStartDebug.bat' is not recognized as an internal or external command"*, on both the
  `C:` and `E:` drives, even though `cmd /c dir` in that same `cwd` shows the file right there. The
  `HKLM/HKCU ... NoDefaultCurrentDirectoryInExePath` policy value that would normally explain this is
  **not** set on this machine, so the exact OS-level cause is unconfirmed -- but `cmd /c .\Name.bat`
  and `cmd /c <absolute path>` both work identically otherwise (verified: cwd is still honored for the
  script's own relative-path behavior). `smoke.py` therefore invokes
  `["cmd", "/c", str(server_dir / "ServerStartDebug.bat")]` -- the resolved absolute path -- while
  still passing `cwd=server_dir`, which is what actually governs the java process's working directory
  (where it looks for `plugins/`, `logs/`, etc.), not the string used to name the `.bat` on the command
  line. This keeps the "launch through that `.bat` via `cmd /c`, stdin piped" contract the task
  requires; only the exact spelling of the batch file's path changed, for a reason that is externally
  verifiable (see the repro notes in `run_scenario()`'s comment in `smoke.py`).
- **`pause` (and this harness's own `--fake` server) writes `Press any key to continue . . . ` with NO
  trailing newline.** `readline()`-based line reading can only ever return a *complete* line (ending in
  `\n` or at EOF); an unterminated prompt like this sits in the pipe forever, unread, until either more
  bytes with a newline arrive or the process exits. Waiting for that exact prompt text to show up as a
  matched *line* is therefore a genuine deadlock, not just unreliable -- the harness's first
  implementation hit this in testing (see "Limitations" below) and hung until its own hard timeout.
  The fix: `ServerProcess.wait_for_quiet_or_pattern()` treats "no new line appended for 3 consecutive
  seconds" as an equally-valid signal that java has finished and `cmd.exe` is now sitting at that
  unterminated prompt, and releases it with a bare newline regardless of whether the prompt text was
  ever observed as a complete line. The literal pattern match is kept as an opportunistic *faster*
  exit path in case some environment does emit a trailing newline there.
- **`taskkill /F /T /PID <pid>`** is used whenever a boot or stop wait exceeds its timeout, killing the
  entire `cmd.exe` -> `java.exe` tree (the `pid` tracked is the `cmd.exe` process; `/T` recurses to its
  children).
- **No `shell=True` anywhere.** Every `subprocess.Popen`/`subprocess.run` call in this file passes a
  list of argv tokens, never a shell-interpreted string, specifically so the repo's `[` `]` `space`
  characters are never re-tokenized by a shell.
- **Process-conflict detection** tries `wmic` (may be absent on newer Windows builds), then falls back
  to PowerShell `Get-CimInstance Win32_Process` (which still exposes the full command line, unlike
  `tasklist`), then falls back to a bare `tasklist /FI "IMAGENAME eq java.exe"` presence check, which
  is explicitly reported as an *unconfirmed* conflict since it cannot see command lines at all.

## `--fake` mode

`smoke.py --fake-server-run [module_id ...]` is a hidden sub-mode of the very same file: when invoked
with that first argument it never touches `argparse` or any harness state, it just runs a tiny
stand-in for Paper (`fake_server_main()`) that:

- prints a handful of Paper-shaped boot lines, one `Loaded module <id> ...` line per module id given
  as an argument, a `Runtime modules: N loaded, 0 failed` line, a canned `[FakeSmoke]`-tagged WARN and
  ERROR (with a fake `Caused by:` + one `at org.luckyraven...` stack frame) so the report's
  error/frame-extraction logic has something real to chew on, and finally `Done (5.123s)! For help,
  type "help"`;
- mirrors every line it prints into `logs/latest.log` under its own cwd, so the "copy `logs/latest.log`
  right after the run" step has something real to copy even in fake mode;
- echoes each command it receives on stdin as `> <command>` and gives a canned response, with `stop`
  ending its own loop cleanly (**no** inner pause of its own -- matching real Paper, where only the
  wrapping `.bat`'s trailing `pause` exists).

`--fake` (the harness-side flag, not `--fake-server-run`) creates a throwaway temp directory
(`tempfile.mkdtemp()`), writes a `ServerStartDebug.bat` there that calls
`"<python>" "<this file>" --fake-server-run <row's module ids>` followed by a real `pause`, and drives
it through the exact same `ServerProcess`/`perform_stop` code path used for the real server -- so a
green `--fake` run is a real end-to-end exercise of the process-control, stdin-driving, log-parsing and
reporting machinery, just never touching the real `E:\Documents\Minecraft\Test Server`, never binding
port 5005/25565, and never spawning a real `java.exe`. It deliberately skips `--deploy`/`--keystone`/
preflight (there is nothing real to deploy to or conflict with) and skips `--restore`'s module-jar
step (prints a note instead).

**What `--fake` is not**: it is not a functional test of the actual plugin's module loader, dependency
resolution, or command output -- the fake server's `Loaded module` lines are driven purely by the
scenario's `modules` list, not by anything reading real jars or a real `module.yml`. It exists solely
to prove the harness's own mechanics (process control that cannot hang, stdin command sequencing,
log parsing, expectation evaluation, report writing) without a ~3-4 minute real Paper boot/shutdown
cycle in the loop.

## Actual test results

### `python smoke.py --fake --rows S1,S2`

```
[fake] Fake server dir: C:\Users\Hashim\AppData\Local\Temp\smoke_fake_server_mjdxf1pk

=== S1: Empty -- no runtime modules deployed ===
[S1] verdict=PASS boot=True stop=True modules=[] errors=2

=== S2: Weapon only ===
[S2] verdict=PASS boot=True stop=True modules=['weapon'] errors=2

=== Summary ===
  S1   PASS  boot=True stop=True modules=[] errors=2 title=Empty -- no runtime modules deployed
  S2   PASS  boot=True stop=True modules=['weapon'] errors=2 title=Weapon only

Summary written to:
  reports/2026-09-08-1136-summary.md
  reports/2026-09-08-1136-summary.json
```

Exit code `0`, completed in well under a minute. Full per-scenario detail:
`reports/2026-09-08-1136-S1.md` / `-S2.md` (with `reports/2026-09-08-1136-S1.log` / `-S2.log`
alongside them -- the fake server's mirrored `logs/latest.log`). Both rows' `no_errors_except` check
passed against the canned `[FakeSmoke]`-tagged WARN/ERROR/`Caused by` lines; both `loaded_modules`
checks matched exactly (`[]` and `['weapon']`); S2's `Disabling`/`onDisabled` check passed against the
fake server's per-module disable lines; `stop_reason` for both was `exited_after_quiet` -- the
quiescence detector, not a literal prompt-text match, is what actually released `pause` in this run
(see "Windows quirks" -- this is the fix for a real deadlock hit during development, not a
hypothetical).

### `python smoke.py --dry-run --deploy --rows S4`

```
=== S4: Turf + Weapon + Cops ===

--- Dry-run plan for S4 ---
  [core_jar] would copy: E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\target\gangland_warfare-0.8.4.jar (FOUND)
             -> E:\Documents\Minecraft\Test Server\plugins
             would first delete existing: E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.8.4.jar
  [modules_sync] target dir: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\modules
                 existing jars there now (would be parked/cleared once): ['gangland-weapon-0.8.4.jar']
                 parked-jar holding dir: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\modules\.harness-parked
                 would place: turf <- E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\target\modules\gangland-turf-0.8.4.jar (FOUND)
                              -> E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\modules\gangland-turf-0.8.4.jar
                 would place: weapon <- E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\target\modules\gangland-weapon-0.8.4.jar (FOUND)
                              -> E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\modules\gangland-weapon-0.8.4.jar
                 would place: cops <- E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\target\modules\cops-n-crooks-0.8.4.jar (FOUND)
                              -> E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\modules\cops-n-crooks-0.8.4.jar
  Command plan (8 commands): ['glw', 'glw help', 'glw help 2', 'glw help 3', 'glw modules', 'glw turf', 'glw weapon list', 'glw cops']
```

Exit code `0`. No process was started (`--dry-run` never calls `ServerProcess.start()`), no file was
copied or deleted (`--dry-run` never calls `deploy_scenario()`, only the read-only
`compute_deploy_plan()`). Note for anyone re-running this: earlier in the same session `target/` had no
top-level core jar and was missing `gangland-weapon-*.jar` from `target/modules/` (every source in this
same plan printed `None (NOT FOUND)` and the "would place" destinations fell back to a
`<prefix>-NOT-FOUND.jar` placeholder name instead of raising); by the time this final run was made,
`target/` had been rebuilt (by another agent in the same coordinated wave -- this repo currently has
several agents working different task groups in parallel) and every source resolved. Both outcomes are
legitimate and both were observed: the harness degrades a missing source to `NOT FOUND` rather than
raising, and still prints the rest of the plan. The `existing jars there now` line correctly reflects
the real server's current `plugins/Gangland_Warfare/modules/` contents (`gangland-weapon-0.8.4.jar`) at
the time -- nothing there was touched by either dry run.

## Limitations

- **Two authoritative-matrix expectations are not automated.** S4's "detainment table migrates" is a
  database fact with no guaranteed console line to grep for; S1's "`/glw help` lists only core
  commands" needs comparing a command count against a moving target (the core's own command set), not
  a fixed string. Both need a human to read the relevant transcript in that row's report rather than a
  boolean this harness computes -- `expect` deliberately has no entry for either rather than a check
  that would silently rubber-stamp the wrong thing. The same applies to D8's "no second `Item
  vocabularies installed:` line" and "no duplicate-listener errors" (T-O9) -- both need a
  count-of-occurrences check `evaluate_expect()` does not implement (`must_contain`/`must_not_contain`
  only test presence/absence, not count), so D8's `expect` omits them and a human reads the transcript.
- **D6's fault counts assume the checklist's default reading, pending a user decision.** `gangland-0.9.0.md`
  flags D6 (Bartizan removed) as PENDING USER DECISION (reviews H m8 / RI I-3): as planned, `civilians`
  declares `Plugins: [Bartizan]` and `turf` declares `Depends: [civilians]`, so `D6`'s `expect` block
  encodes `mail`/`turf`/`npcshops` loading (`3 loaded, 3 fault(s)`) -- the alternative resolution (a
  turf-side seam that drops the `Depends:`) would change turf to also skip
  (`2 loaded, 4 fault(s)`), which is a different `expect` block, not just a re-run.
- **This harness runs inside a larger coordinated effort** (`../README.md`, the "Bartizan Wave" board;
  this task is listed there as `A · smoke harness | agent H1`). Per this task's own instructions this
  session only ever wrote inside `smoke/`; it did not edit the shared board, the linked artifact page,
  or its database, even though the board describes an `smoke` collection and a `build_board.py`
  regeneration step -- that reconciliation is left to whoever owns the board, since guessing at an
  unseen schema on shared coordination state seemed riskier than leaving it for a session that has more
  context than a `grep` of the file gives.
- **Full real-server run not executed.** Everything above was verified with `--fake` (process-control,
  stdin driving, stop handshake, log parsing, report/summary writing) and `--dry-run` (deploy planning
  against the real paths). No row was actually run against the real `E:\Documents\Minecraft\Test
  Server` Paper process in this session, so the `Done \(` boot-detection regex, the real console's
  actual `stop`/`pause` timing, and the real module loader's real fault-message text
  (`module.dependency.missing`, `module.host_api`, etc. for S6/S7) are unverified against a live
  server. The stdin-piping mechanism itself (`cmd /c <bat> ` with `stdin=PIPE`) was exercised
  end-to-end against a real `cmd.exe` + `pause` in the debugging repro used to find the two Windows
  quirks above, just not against the real `java.exe -jar paper-1.21.11.jar`.
- **`Press any key to continue . . .` prompt is not directly observable.** As explained above, this is
  a genuine `readline()` limitation, not a workaround of convenience -- the harness's stop handshake is
  correct because it does not depend on seeing that exact text, but if some other unexpected prompt
  appeared mid-shutdown, this harness would not distinguish it from ordinary quiescence.
- **Locale-dependent text.** `PRESS_ANY_KEY_PATTERN` assumes English Windows (`"press any key"`,
  case-insensitive) as its opportunistic fast path; on a non-English `cmd.exe` this pattern simply never
  matches and the harness silently falls back to the quiescence path, which is locale-independent -- no
  crash, only a slightly slower (up to 3s) stop per run.
- **`wmic` may already be removed** on newer Windows 11 builds (it was present on this machine); the
  PowerShell fallback is what actually matters going forward.
- **`ERROR`/`WARN` substring matching is case-sensitive and literal**, per the task's own filter list
  (`ERROR`, `WARN`, ...); a log line using lowercase `error` in prose (not the level tag) would not be
  picked up, and this was left as specified rather than guessed at.
- **The `target/` directory's exact contents are a moving target** in this environment (it was
  observed to exist with a partial module set, then not exist at all, within the same session) --
  `--deploy` runs against it will only succeed once a real `mvn clean package` has populated it, which
  this harness deliberately never triggers itself.
