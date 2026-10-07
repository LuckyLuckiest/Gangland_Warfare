# Gate review GA — Bartizan groups A+B (B1–B4)

Reviewer R-B-AB (Opus, feature-dev `code-reviewer`, read-only), 2026-09-08 ~19:10. Reviewed executor X-B-AB's output
in `E:\Programming\java\Bartizan` against `bartizan.md` §Contract, §1.1 group B, tasks B1–B4, the §7 notes and
`architecture/PICK.md`. Transcribed by the orchestrator; orchestrator actions are marked **→**.

## Verdict: GA PASS WITH FIXES

The skeleton and the 25-file port are structurally sound: every ported file is line-for-line identical to its Gangland
original apart from `package`/`import` lines and one deliberately dropped import. No blockers.

## Findings

### Major

**M1 — `bstats-bukkit` is shaded unrelocated.** `bartizan-plugin/pom.xml:32-48` (shade block) and `:96-99`
(compile-scoped `org.bstats:bstats-bukkit`). The shade block was copied from `keystone-plugin/pom.xml`, which needs no
relocations because Keystone shades only its own modules; Bartizan additionally shades a third-party library that
bStats itself requires to be relocated, and Gangland does relocate it (`gangland-build/pom.xml:48-51` →
`org.luckyraven.gangland.dependency.bstats`). Spigot's `PluginClassLoader` shares classes across plugins, so two
plugins shipping different unrelocated `org.bstats.bukkit.Metrics` resolve to whichever loaded first.
Fix: add a `<relocations>` block relocating `org.bstats` → `org.luckyraven.bartizan.dependency.bstats`. Non-blocking
for GA (no plugin sources yet); must land before B14/B21.
**→** Checklist B2 text amended. Pom edit deferred until executor X-B-CDE reports (it may touch the plugin pom for
the test-only module build); lands in the GD fix-up commit.

**M2 — Gate GC as written cannot pass (checklist defect).** `dto/AmmunitionData.java` (group B, ported) imports
`api.ammo.Ammunition`, a group-D file that arrives only in B6, so `bartizan-api` cannot compile after B5 alone.
Fix: move `Ammunition` into B5 or redefine GC.
**→** GC redefined in place: after B5 the build must fail only on `Ammunition` (+ `CombatEligibility` if not created);
exit 0 is B6's Done-when / gate GD. B5's Done-when, the gate line and the §7 row updated; executor X-B-CDE messaged.

### Minor

**m3** — `api/weapon/dto/ThrowableData.java:36` keeps `{@link PotionEffectParser}` after its import was (correctly)
dropped; dead javadoc link. Fix: `{@code PotionEffectParser}`. **→** handed to X-B-CDE (owns the api tree now).

**m4** — `api/weapon/Weapon.java:342` javadoc says "configured `%gangland_*%` placeholders"; the only `gangland`
string in the api tree and it contradicts README/CLAUDE.md's "zero gangland symbols" claim. Fix: "configured
PlaceholderAPI placeholders". **→** handed to X-B-CDE.

**m5** — `bartizan-api/pom.xml:43-47` has test-scoped `mockito-core`, which B2 does not list and §7 did not record as
a drift (forward reference to B19's `ModifierHandlerTest`). No compile-scope leak. **→** recorded as drift #4 in the
B2 row; dependency kept.

**m6** — `README.md:35-36` links `documentation/bartizan-api.md` and `documentation/migration.md`, which B21 creates
later. **→** annotated "(added in B21)".

### Verified clean

Poms: groupId `org.luckyraven.bartizan`, `<revision>0.1.0</revision>`, `maven.compiler.release` 17, `bukkit.version`
1.16.5-R0.1-SNAPSHOT, flatten-maven-plugin 1.6.0 configured exactly like Keystone's, pluginManagement and the
`release` profile identical, `deploymentName`/`excludeArtifact` present; every `org.luckyraven:keystone-*` entry is
`provided` in both modules; the ViaVersion repository and provided `viaversion-api` with the T-09 comment present;
`log4j-core` test-scoped in the module; no test dependency reaches compile scope. XSeries at `provided` is correct
(Keystone shades it unrelocated). `.gitignore` covers `target/`, `.flattened-pom.xml`, `.idea/`, `*.iml`, `.vscode/`,
`*.log`, `graphify-out/`. README/CLAUDE.md contradict nothing in §Contract or PICK.md. B4 port: every `package` line
matches its directory; every import resolves inside `api.weapon[.dto|.durability|.reload|.recoil|.spread|
.modifiers.action]`, `api.ammo`, `org.luckyraven.keystone.{exception,util,item,sound}`, XSeries, Bukkit, Lombok /
JetBrains or the JDK; zero `net.minecraft`, `craftbukkit`, `io.papermc` or `org.luckyraven.gangland` symbols; line
counts identical to the originals (ThrowableData 67→66 for the dropped import); no collapsed method bodies.

## Drift rulings

1. `keystone-npc` added to root `dependencyManagement` — **acceptable**; B1's list was inconsistent with B2.
   **→** B1 text amended so nobody "cleans it up".
2. `keystone-bean` / `keystone-persistence` transitively at `provided` through `keystone-npc` — **acceptable**;
   B2's Done-when was wrong (`keystone-npc/pom.xml:31-43` compile-depends on both; `citizens-main` is
   provided→provided and drops out entirely, which also makes the missing Citizens repository harmless).
   **→** B2 Done-when reworded. Reviewer's optional `<exclusions>` hardening on the `keystone-npc` dependency is
   **not** applied: `NpcWeaponFactory`/`NpcWeaponController` compile against `keystone-npc` types whose signatures
   may mention bean/persistence types, and javac would then fail on missing class files for no API benefit.
3. 13-symbol closure instead of B4's 5 — **acceptable**, the executor's read is correct (`ReloadType`,
   `ProjectileType`, the six `modifiers/action/*` classes are group C; `Ammunition` is group D = M2).
   **→** B4 Done-when now lists the 13 names.

## Not verifiable by the reviewer (no shell)

Byte-for-byte equality of the 25 ports (established by line counts, import sets, a full signature list for
`Weapon.java` and one full byte comparison); the LF line endings of the new files (Git on this machine has
`core.autocrlf` on, so the index holds LF and checkouts get CRLF — no `.gitattributes` needed); the executor's
recorded Maven outcomes and git state (the orchestrator committed the skeleton as `c298eb6` on `master`).
