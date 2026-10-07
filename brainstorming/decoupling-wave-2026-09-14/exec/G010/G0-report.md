# G0 report — 2026-09-20

Status: DONE

## What changed
- `pom.xml:57` — `<revision>0.9.2</revision>` → `<revision>0.10.0</revision>`
- `pom.xml:70` — `<keystone.version>1.9.2</keystone.version>` → `<keystone.version>1.11.0</keystone.version>`
- `gangland-api/src/main/java/org/luckyraven/gangland/GanglandApi.java:13` — `VERSION = "1.1"` → `VERSION = "2.0"`
- `Host_Api:` line bumped to `2.0` in all six `module.yml`s (`src/main/resources/module.yml`, all were line 6):
  `gangland-features/cops-n-crooks`, `gangland-features/gangland-civilians`, `gangland-features/gangland-gadget`,
  `gangland-features/gangland-mail`, `gangland-features/gangland-npc-shops`, `gangland-features/gangland-turf`
  (previously a mix of `1.0` and `1.1`)

## Deviations from the plan
None. No Keystone API drift surfaced — the reactor compiled and tested clean against Keystone 1.11.0 on the
first pass, so no adaptation code was needed. No module-loader test expectations needed changing:
`ModuleInstallsTest.java` (`gangland-impl/src/test/java/.../command/sub/module/ModuleInstallsTest.java`) builds
its own local `PluginVersion`/`ModuleDescriptor` fixtures with literal `"1.0"`/`"0.9"`/`"1.0.4"` strings — it
tests `ModuleInstalls.inspect`'s major/minor-floor comparison logic in the abstract, not tied to
`GanglandApi.VERSION` or any specific host line, so it needed no edit for this bump. Grepped the whole reactor
for `Host_Api`/`hostApi`/`GanglandApi.VERSION` in `*Test.java` first to confirm nothing else hardcodes the old
line.

## Red-first evidence
N/A for this gate — pure version-constant bump, no behavior change, no new/flipped test.

## Build
- Baseline (pre-edit, confirms 0.9.2 state was green before touching anything): `mvn clean install -DskipTests` → `BUILD SUCCESS`
- Post-edit compile check: `mvn clean install -DskipTests` → `BUILD SUCCESS`, all 21 reactor modules SUCCESS
- Post-edit full test run: `mvn test` → `BUILD SUCCESS`, last module summary `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
  (turf, cops-n-crooks, gadget, npc-shops, and every other module's suite all `Failures: 0, Errors: 0` — full
  log grepped for `Failures: [1-9]|Errors: [1-9]`, zero hits)

## Docket ids touched
None.

## Subagents used
None — mechanical constant bumps (2 files' single-line edits + 6 identical one-line YAML edits) done directly;
spawning a haiku agent for this would have cost more than it saved. Build/verification done directly.

## Concerns / open questions
- None for this gate. `target/classes/module.yml` copies under each module still show the old `Host_Api` values
  from before `mvn clean`, but those are build output regenerated on the next `mvn clean install`/`test` (already
  confirmed above) — not source, not committed.
- Per the brief: this is its own commit. Files touched (8 total): `pom.xml`,
  `gangland-api/src/main/java/org/luckyraven/gangland/GanglandApi.java`,
  `gangland-features/cops-n-crooks/src/main/resources/module.yml`,
  `gangland-features/gangland-civilians/src/main/resources/module.yml`,
  `gangland-features/gangland-gadget/src/main/resources/module.yml`,
  `gangland-features/gangland-mail/src/main/resources/module.yml`,
  `gangland-features/gangland-npc-shops/src/main/resources/module.yml`,
  `gangland-features/gangland-turf/src/main/resources/module.yml`.
  Stopping here per instruction — waiting for the orchestrator to commit before continuing with WS1 G3 in this
  same report file.
