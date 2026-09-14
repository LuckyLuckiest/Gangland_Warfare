# Keystone (KS) — review lead notes, 2026-09-10

Repo: `E:\Programming\java\Keystone`, branch `phase-h8-item-npc`, HEAD `61621a8`.
Keystone is the shared infrastructure plugin behind Gangland Warfare, Bartizan and Oriel — every finding here lands on
all three consumers at once, which is why layering and shared-classloader violations are tiered as real bugs rather
than style.

Graph freshness checked before ordering: `graphify-out/graph.json` rebuilt 2026-09-10, newer than HEAD (2026-09-09), so
the graph was trusted without a refresh. `graphify god-nodes --top 20` confirmed the hubs the phase-1 map named:
ConfigReport (108 edges), Argument (87), Fault (79), Database (77), DatabaseHandler (67), Table (66),
DependencyContainer (61), AbstractNpc (56), SqliteBackend (52), Result (50), Tree (49), Diagnostics (49).

## Scope decisions — which system got which scanner, and why

The phase-1 map's package lists were treated as a starting point only; every order defines its scope as a literal
`find <dirs> -name '*.java'` command run at the repo root, so the scanner enumerates its own exact file list and cannot
silently miss a file the mapper omitted. The mapper's file counts were checked against the real tree first
(`keystone-common` is 88 main files, not the ~49 the map implies for UC; `keystone-persistence` is 65, split across CF,
DB, SC and DG).

| System | Model | Why |
|---|---|---|
| DB database-repos | sonnet | SQL generation, transactions, HikariCP lifecycle, batch upsert, schema diffing — all semantic |
| BN bean-container | sonnet | Reflection scans, phase ordering, cycle detection, ReflectionGuard interaction |
| CF config-file | sonnet | File I/O, recovery-loop data loss, charset handling, DSL parsing |
| CM command-framework | sonnet | Permission enforcement and recursive tree matching — the permission-hole surface |
| IT item-framework | sonnet | Item duplication/loss, NBT reflection, static registry risk |
| MO module-loader | sonnet | Classloader/jar handles, artifact download, descriptor gating |
| NP npc-system | sonnet | Citizens soft-dependency holes, task leaks, entity lifecycle |
| HK hooks-integration | sonnet | Money. Highest P0 density expected, and it delivered the only economy P0 |
| DG diagnostics-error | sonnet | Netty packet threading, reflection degradation, the Diagnostics static hub |
| SC scheduling-cooldowns | sonnet | Async timer safety, cooldown thread safety, persistent write path |
| PH placeholders | sonnet | Recursive expansion / expansion-bomb risk and provider isolation — not a grep-able sweep |
| UC utils-helpers | sonnet | Largest system (49 files); hand-rolled data structures (Tree backs the command system), PDC persistence, ReflectionUtil, plus testkit lifecycle — semantic, not mechanical |
| conventions sweep | haiku | 9 purely grep-able rules, project-wide; findings routed into the owning system |

Only UC and PH were candidates for haiku. Both were given sonnet: UC contains hand-rolled containers and the PDC data
path, and PH's recursion/provider-isolation questions need call-chain tracing. The haiku budget went to the one order
that is genuinely mechanical — the project-wide conventions sweep.

Concurrency held at 3 scanners throughout. Every order carried the tier rubric and output format verbatim, the exact
scope command, entry points, the repo's hard design rules (layering, 1.16.5 floor, Java 17, shared-classloader,
testkit shading, Result/Diagnostics over throw-or-swallow), and the standing rules: read every file end to end, trace
entry points not files, cite and quote lines actually read, name a covering/pinning test, prefer few real findings,
no sub-agents, no source edits, one output file.

## Orders given (one line each)

1. **DB** — `persistence/database/**` + `repository/**` + `audit/**` (~37 files); trace DatabaseHandler.connect/setType fallback, upsertAll dialect chain, TableBackend/Attribute flags, AbstractRepository.saveAll, RepositoryRegistry scan, SchemaDiff/SchemaMigrations, AuditLogService, HikariCP lifecycle; explicitly asked to confirm-or-refute Gangland docket CL-01 and CL-02.
2. **BN** — `keystone-bean/**` (23 files); trace scan → instantiate → phase loop → reload/shutdown, BeanGraph sort, DependencyContainer resolution, ListenerService/CommandService scans, ReflectionGuard holes.
3. **CF** — `persistence/config/**` + the five persistence-root file classes (~28); trace FileManager.initializeAll recovery loop, FileHandler jar→disk, ConfigSerializer/NodeReader, DSL parsers; watch for config data loss.
4. **CM** — `keystone-command/**` (19); trace dispatch → argument tree → permission checks → tab completion → Brigadier; permission holes are the priority.
5. **IT** — `keystone-item/**` (20); trace ItemBuilder/clone, converter and refresher registries, ItemDefinitions static risk, NBT seam; item duplication or loss is the priority.
6. **MO** — `keystone-module/**` (17); trace load/discover/enableAll, descriptor gating, ModuleClassLoader handles, artifact resolver, update service; main-thread network I/O and jar handle leaks.
7. **NP** — `keystone-npc/**` (19); trace spawn/navigate/combat/destroy, NpcSupport degradation, mark persistence; Citizens-absent crashes and task leaks.
8. **HK** — `keystone-hooks/**` (17); trace EconomyHandler, Bank, Currency, VaultEconomyProvider, vault permission providers, PAPI; money creation/loss and ignored EconomyResponse.
9. **DG** — `diagnostics/**` + `result/**` + `nms/**` + `persistence/message/` (~27); trace Diagnostics install/report, Guard, ReflectionGuard, Result/Pipeline, NmsVersion/NmsCache, PacketAdapter, PlayerInputInterceptor; Netty threading and handler cleanup.
10. **SC** — `timer/**` + `cooldown/**` + `update/**` + `persistence/cooldown/` (14); trace Timer.start/stop per subclass, cooldown expiry, UpdateChecker network path; async-touching-Bukkit and uptime leaks.
11. **PH** — `placeholder/**` (12); trace registry, replacer token scanner, recursive expansion, effect chain; expansion bombs and provider isolation.
12. **UC** — `util/**`, `datastructure/**`, `color/**`, `meta/**`, `permission/**`, `logging/**`, `message/**`, `sound/**`, `exception/**` + `keystone-testkit/**` (~49); depth on Tree, ChatUtil, PdcPlayerMetaService, ReflectionUtil, DurationParser, MockPluginFactory; testkit file-lock and StaticResets coverage.
13. **Conventions sweep (haiku)** — 9 grep rules project-wide: Paper imports, raw loggers, raw version-drifting enum `valueOf`, unchecked `getWorld()`, async tasks touching Bukkit APIs, consumer symbols inside Keystone, testkit/Mockito reaching keystone-plugin's shade, Java 21 features, post-1.16.5 Bukkit API.

## Verification

Every raw finding was re-opened at its cited lines before it entered a final file. Where a claim turned on external
behaviour rather than repo code, the Spigot 1.16.5 API sources in the local Maven repository were consulted directly
(`spigot-api-1.16.5-R0.1-...-sources.jar`) rather than trusted from memory — this settled three cases: the CF stream
leak (culled), the SC Timer P0 (confirmed), and the sweep's `getWorld()` hit (culled).

## Culled findings, with reasons

| Raw finding | Reason culled |
|---|---|
| CF #7 — `FileManager.loadFromResources` never closes its InputStream | **False positive.** Bukkit 1.16.5 `FileConfiguration.load(Reader)` closes the reader in a `finally` block (verified in the Spigot API sources), which closes the underlying stream on both the success and exception paths. No leak exists. |
| Conventions sweep #1 — unchecked `getWorld()` at `DefaultPlaceholderProvider.java:63` | **False positive.** `Entity#getWorld()` is declared `@NotNull` in the Spigot 1.16.5 API; only `Bukkit.getWorld(String)` is nullable, and this file never calls it. |
| NP #7 — `NpcMetadata` hardcodes Gangland trader/banker/turf keys into shared infra | **Deliberate and documented.** The class javadoc explains these are persisted Citizens metadata keys on live servers and that renaming them orphans every already-spawned NPC on upgrade, so they are append-only by design. Filing it would push a change that breaks production data. |
| BN #4 — `DependencyContainer.getInstance(Class)` resolves ambiguity by picking the first registered | **Documented intended behaviour** ("Returns the first registered instance if multiple exist"), and `BeanFactory` fails fast on the same ambiguity separately. No concrete harm demonstrated. |
| IT #2 + #4 — `setAmount` bounds check and the null-tolerance inconsistency | **Merged**, not dropped: same method, same fix. Kept as item-framework #4. |
| BN #2 + #6 — named-bean collision in the container and in BeanGraph | **Merged** into bean-container #1: one root cause (bean names are never checked for uniqueness), one fix, two sites recorded in the observation. |
| PH #5 + #6 — unwired flash effect layer, and its classloader-wide static state | **Merged** into placeholders #5: one decision (wire it and namespace the state, or delete it); filing separately would double-count. |
| UC #12 — `YamlMessageProvider.getString` can return null and `ChatUtil.color()` NPEs on it | **Unverifiable citation.** The finding cites `YamlMessageProvider.java:44-49`; that range does not exist in the file. Dropped rather than rewritten, because I could not confirm the described null path from the code in the budget available. Worth a targeted re-check if the orchestrator wants it. |
| UC #11 — `PermissionWorker.permissionRefactor` bare `startsWith` | **Kept with a corrected citation**, not culled. The finding cited lines 186-192 of a 66-line file — a fabricated range — but the defect is real and verified at line 30 (`!permission.startsWith(this.indicator)` with no dot boundary). Filed as KS-UC-09 with the true location. |
| UC #2 — SpellChecker transposition math: P1 → **P3** | The algorithmic defect is real, but its only consumer is the did-you-mean suggestion behind an unknown command, so the impact is suggestion quality; no wrong command executes. |
| ~~Gangland **CL-01**~~ | **The DB scanner's refutation was overturned on my own re-verification — now filed as KS-DB-12.** See "Cross-project root causes checked" below. |
| ~~Gangland **CL-02**~~ | **Also overturned — now filed as KS-DB-13.** See below. |

## Tier changes I made against the scanners' proposals

Scanners proposed 4 P0s; 2 survived. The two demotions and the notable promotion:

- **CM #1 (onHelp permission bypass): P0 → P1.** Real and confirmed — `runExecute` checks `hasPermission` and the help
  branch does not, while `onHelpAll` deliberately filters through `permissibleCommands(sender)`. But the consequence is
  reading a restricted command's help text, i.e. information disclosure, not executing anything. If the orchestrator
  counts command-structure disclosure as a permission hole proper, this moves back to P0.
- **CM #2 (confirm-substring): P0 → P1.** The substring match is real (`arg.toLowerCase().contains("confirm")`), but the
  destructive-action gate still holds: the lock test lives on the *registered tree node's* `ConfirmArgument.executeArgument`,
  not on the input wrapper, so typing "confirmed" cannot fire an unconfirmed destructive action. It corrupts token
  matching, which is P1.
- **PH #1 (provider touches Bukkit world API): P0 → P2.** `%biome%` really does call
  `online.getLocation().getBlock().getBiome()`, a chunk-loading read, but no async caller exists inside Keystone, so the
  P0 rubric line ("main-thread Bukkit API from async") is not met by anything in this repo today. Filed as a latent trap
  with an explicit note that it becomes P0 the moment a consumer renders a scoreboard off-thread.
- **NP #5 (EntitySpawner shrink loop): P2 → P1.** A zero or negative shrink step spins forever *on the main thread* — a
  full server hang from one mistyped config value, which outranks the "edge-case crash" P2 line.
- **DB #4 (double-quoted identifiers on MySQL): P1 → P2.** Real, but the rubric names backend-specific failures as P2,
  and the code's own javadoc already concedes the ANSI_QUOTES dependency.
- **SC #3 (`CooldownFormat.human(ZERO)` → "1s"): P1 → P2.** Display-only defect.

## Integrity check on the finished files

A final pass validated every final file: 8 `~~`-separated fields on every line, no embedded newlines, numbering
contiguous 1..n per system. That pass also caught nine tier fields that did not match the determinations recorded in
this document (P1s sitting on disk as P2/P3 in BN, CM, CF, DB, DG, MO and NP). They were corrected in place, changing
only the tier field, and the schema re-validated afterwards. The counts in the final table are computed from the files
as they now stand, so the table, the files and this document agree.

## Cross-project root causes checked

The Oriel and Gangland leads named five Keystone root causes absent from the scanner output. I verified all five
against the code myself. **All five are real and all five are now filed** — four in `database-repos`, one in
`config-file`, each carrying its `also Oriel OR-xx` / `also Gangland CL-xx` cross-reference in the observation.

| Claim | Verdict | Filed as |
|---|---|---|
| (1) OR-DB-01 — `AbstractJdbcBackend.applySchema` drops undeclared columns | **Real, and worse than reported.** The `for (String drop : diff.toDrop())` loop issues `DROP COLUMN` unconditionally, with no opt-out and only a `log.info` *after* the data is gone. It does not need a failed probe to bite: a plain downgrade to a plugin version whose schema lacks a newer column destroys that column on the next boot. I checked `MysqlBackend.readLiveColumns` and it is correctly scoped (`TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?`), so the mechanism is the unconditional drop itself rather than a mis-scoped probe. Tiered **P0** — irreversible data loss. | KS-DB-10 |
| (2) OR-DB-02 — `AuditLogService` swallows `SQLException` without Diagnostics | **Real.** `catch (SQLException ex) { log.warn(...) }` with no `Diagnostics.report` anywhere in the class; `DatabaseFaultSink` repeats it. Compounds KS-DB-04, which makes every one of these writes fail on a default MySQL server. | KS-DB-11 |
| (3) OR-DI-03 — `NodeReader.asDouble().min(0)` cannot reject NaN/Infinity | **Real.** `Double.parseDouble` accepts `NaN`/`Infinity` and marks the value VALID; every NaN comparison is false, so `min`'s `value < min` and `max`'s `value > max` both pass it through. The class javadoc's own example is a `Sell_Price_Ratio`, so a config typo poisons price arithmetic silently. | KS-CF-07 |
| (4) CL-01 — MySQL failure with fallback off leaves `type==MYSQL, database==null` | **Real, still present at 1.9.1.** My DB scanner reported this fixed and it was half right: `enforceType` *does* now null the field and rethrow, which removes the old non-null-but-unconnected instance — but `setType` catches that rethrow and calls `useSQLite`, which opens `if (!settings.isSqliteFailedMysql()) return;`. With fallback disabled that returns silently, leaving `type == MYSQL` and `database == null` exactly as the Gangland docket describes. `PluginException extends RuntimeException`, so the catch does fire. Tiered **P1**: the missing piece is a loud fail-fast, and the NPEs are its symptom. | KS-DB-12 |
| (5) CL-02 — `saveAll` copies live `map.values()` on the calling thread | **Real; the scanner refuted a weaker version of the claim.** `saveAll` does snapshot synchronously into `new ArrayList<>(collection)` — but that only helps if *saveAll itself* is called from the main thread. `RepositoryRegistry.saveAll` → `saveAllFromMemory` → `saveAll(dataSupplier.get(), …)`, and a data supplier is conventionally a live view like `() -> userMap.values()`. Driven from an async autosave timer, the copy iterates a main-thread-mutated map. The registry's `catch (Exception e) { log.error(...) }` then reduces the resulting CME to one line and that repository's data is never written. No main-thread requirement is documented anywhere. | KS-DB-13 |

Lesson for the audit trail: on both Gangland claims the scanner stopped at the first method that looked correct
instead of following the call chain out to its actual caller. Both were caught only because the claims were
re-verified by hand against the source rather than accepted from the scanner's report.

## Open questions for the orchestrator

1. **Is command-structure disclosure a "permission hole"?** KS-CM-01 lets any player read a restricted subcommand's help.
   I tiered it P1 (discloses, does not execute). If the cross-project convention treats any permission-check bypass as
   P0, it should be re-tiered.
2. **KS-HK-02 (negative deposit) may deserve P0.** `depositAmount` with a negative delta drives the balance negative and
   the resulting `depositPlayer(negative)` is rejected by most economies, leaving the player at zero — genuine money
   loss. I held it at P1 only because the trigger is a *consumer* passing a negative delta; the Keystone-side defect is
   the missing validation at the boundary. Flagging it because "money loss" is otherwise an automatic P0.
3. **KS-MO-02 (artifact path traversal) is the one security-shaped finding.** It needs a hostile or compromised Maven
   repository, which the admin configures and therefore trusts. Tiered P1. If the docket has a security lane it may
   belong there instead.
4. **Two P0s share a root shape worth a cross-project check.** KS-SC-01 (`Timer` cannot be restarted because
   `BukkitRunnable` is single-use) will fire in *any* consumer whose reload pipeline stops and restarts a timer — worth
   asking the Gangland/Bartizan/Oriel leads whether they have unexplained `IllegalStateException: Already scheduled as`
   reports, since the crash surfaces in the consumer, not here.
5. **Deprecated legacy stack.** Four DB findings (5, 6, 8, 9) sit in the `Database`/`SQLite`/`MySQL` stack that is
   `@Deprecated(since=1.6.0)` and removed in 2.0.0. They are tiered on merit; if the docket prefers to sink deprecated
   code to P3 wholesale, those four move.
6. **Javadoc layering violations are filed in two systems** (KS-DG-08 for `diagnostics/`+`result/`, KS-PH-07 for
   `placeholder/effect/`). Same rule, different files and owners — deliberately not merged, but the orchestrator may
   want them as one docket entry.
7. **Two scanner citations were fabricated**, both in UC (`PermissionWorker.java:186-192` and
   `YamlMessageProvider.java:44-49`, in files of 66 and fewer lines respectively). One defect was real and was kept
   with a corrected location; the other was dropped. Every other cited range in every system was confirmed to exist.
   Worth knowing when weighing scanner output on the other three projects.
8. **The conventions sweep came back clean**, which is a result in itself: no Paper imports, no raw loggers, no
   testkit/Mockito reachable from the shaded jar, no Java 21 features, no post-1.16.5 API. Only one raw
   `Material.valueOf` exists in the whole reactor (filed as KS-IT-01).
