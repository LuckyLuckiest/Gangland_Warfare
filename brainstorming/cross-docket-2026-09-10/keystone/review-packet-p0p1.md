# Keystone P0/P1 findings for independent review (Keystone repo: E:\Programming\java\Keystone, Maven modules keystone-bean, keystone-command, keystone-common, keystone-hooks, keystone-item, keystone-module, keystone-npc, keystone-persistence, keystone-plugin, keystone-testkit)

### [bean-container #1] P1 — Duplicate default @Bean names silently overwrite each other in namedInstances and BeanGraph.byName
Location: keystone-bean/src/main/java/org/luckyraven/keystone/bean/autowire/DependencyContainer.java:44-50
Observation: A @Bean method's default name is just the method name: `String name = beanAnnotation.name().isEmpty() ? method.getName() : beanAnnotation.name();` (BeanFactory.java:409). Two @Configuration classes each declaring a same-named method (e.g. two `settings()` beans in a consumer with many configuration classes) therefore produce the same name, and both registration sites overwrite blindly with no check or warning: `namedInstances.put(name, instance);` here, and `byName.put(def.name(), def);` at BeanGraph.java:39. A later @Qualifier("settings") injection then silently receives whichever bean was registered last, and BeanGraph.resolveProducer draws its dependency edge from `return byName.get(req.qualifier());` (BeanGraph.java:87) without ever comparing req.type(), so the graph edge can point at a bean of an entirely different type. Nothing logs the collision, so a consumer gets a wrong-but-plausible object injected with no diagnostic at boot.
Fix: Detect a duplicate BeanDefinition name at registration and fail fast (or namespace the default name by declaring @Configuration class)
Tests: -
Confidence: High

### [bean-container #2] P1 — ListenerService.registerGuarded falls back to registerEvents after per-method registration already partly succeeded
Location: keystone-bean/src/main/java/org/luckyraven/keystone/bean/listener/ListenerService.java:118-133
Observation: registerGuarded loops the listener's methods calling `pluginManager.registerEvent(eventType, listener, handler.priority(), executor, plugin, handler.ignoreCancelled());` one at a time, wrapped in a single try. The catch is `catch (Throwable t)` and its recovery is `pluginManager.registerEvents(listener, plugin);` which registers every @EventHandler in the class again. If the loop throws part-way — Bukkit's registerEvent raises IllegalPluginAccessException for an event class with no static getHandlerList, so handler #3 failing after #1 and #2 succeeded is a concrete trigger — the earlier handlers end up registered twice and every matching event runs them twice. In a consumer that is duplicated gameplay effect on one event (double payout, double item grant), which is why this is filed above P2.
Fix: Track the methods already registered and unregister them (or register into a staging list and commit only on success) before falling back
Tests: ListenerServiceGuardTest (covers only a listener whose very first registerEvent throws, not a partially-registered multi-handler class)
Confidence: High

### [command-framework #1] P1 — CommandManager.onHelp renders a subcommand's help without the permission check runExecute enforces
Location: keystone-command/src/main/java/org/luckyraven/keystone/command/CommandManager.java:199-200
Observation: The dispatch loop picks between execution and help with `if (Arrays.stream(args).anyMatch("help"::equalsIgnoreCase)) onHelp(entry, sender, args); else entry.getValue().runExecute(label, sender, args);`. runExecute opens with `if (!sender.hasPermission(permission)) { sender.sendMessage(CommandMessages.noPermission()); return; }` (Command.java:78-81) but onHelp goes straight to `entry.getValue().help(sender, page);` with no check at all. Any player can therefore type `/<label> <restricted-subcommand> help` and read the full syntax and description of a command they cannot run. The sibling path onHelpAll deliberately iterates `permissibleCommands(sender)`, so permission-filtered help is clearly the intent and this branch is the hole; filed P1 rather than P0 because it discloses command structure but executes nothing.
Fix: Check sender.hasPermission(command.getPermission()) in onHelp before calling help(), as onHelpAll already does via permissibleCommands
Tests: -
Confidence: High

### [command-framework #2] P1 — Argument.execute coerces any token merely containing the substring confirm into the literal confirm token
Location: keystone-command/src/main/java/org/luckyraven/keystone/command/argument/Argument.java:146-149
Observation: Every user token is wrapped with `if (arg.toLowerCase().contains("confirm")) return new ConfirmArgument(plugin, tree); return new Argument(plugin, arg, tree);`, and ConfirmArgument's constructor calls `super(plugin, "confirm", tree, action)` so its arguments array is exactly {"confirm"}. Any free-text value containing the substring — a mail body word like "confirmed", a gang name "Confirmation" — is therefore rewritten to the literal token "confirm" before tree matching, so it stops matching the OptionalArgument node it belongs to and instead matches a confirm branch. The destructive-action gate itself still holds, because the lock test lives on the registered tree node's ConfirmArgument.executeArgument (`if (!lock.isLocked(sender))`), not on this input wrapper — so this is input corruption, not a confirm bypass.
Fix: Match the token exactly (equalsIgnoreCase("confirm")) instead of contains, when wrapping input args
Tests: -
Confidence: High

### [command-framework #3] P1 — BrigadierPaperListener.handle swallows ReflectiveOperationException, leaving the unfiltered permission-leaking tree in place
Location: keystone-command/src/main/java/org/luckyraven/keystone/command/brigadier/BrigadierPaperListener.java:132-134
Observation: The entire per-event body is wrapped in `catch (ReflectiveOperationException ignored) { // No realistic recovery — the unfiltered tree (if any) stays in place. }` with no log and no Diagnostics report. This class exists specifically to remove PaperCommodore's unfiltered shared command node and replace it with a per-player permission-filtered tree, so when the reflection fails the fallback state is exactly the leak it was written to prevent: every player's client sees completions for commands they cannot run. Because the catch is silent, an admin gets no signal that command-completion filtering has stopped working on their server build.
Fix: Report the reflective failure through Diagnostics (once) instead of an empty catch, so the degraded state is visible
Tests: -
Confidence: High

### [command-framework #4] P1 — ArgumentLock never releases a sender who disconnects mid-confirmation, retaining the Player strongly
Location: keystone-command/src/main/java/org/luckyraven/keystone/command/argument/ArgumentLock.java:12-28
Observation: The lock set is `private final Set<CommandSender> lockedSenders = new HashSet<>();` populated by lock() and emptied only by unlock(), which fires solely from ConfirmArgument.executeArgument on a successful second invocation. A player who triggers the first half of a confirm gate and then logs out is never removed, so the set retains a strong reference to a Player object — and through it the entity and its world references — for the remaining uptime of the server. Every abandoned confirmation adds another, so this grows monotonically on a busy server. There is no correctness impact on rejoin (the returning player is a new CommandSender and correctly unlocked), making this purely an uptime leak.
Fix: Remove the sender on PlayerQuitEvent, or key the set on UUID with a timeout instead of holding CommandSender
Tests: -
Confidence: High

### [config-file #1] P1 — FileManager.runInitializer regenerates the live config from jar on any Exception, not just parse failures
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/FileManager.java:253-277
Observation: runInitializer wraps the whole consumer callback in `catch (Exception first)` with no filtering on exception type, then calls `handler.createNewFile()` on any failure. createNewFile moves the admin's live file aside — `Files.move(file.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);` (FileHandler.java:125) — and writes the jar default in its place, logging "is an old build or corrupted, creating a new one". So a bug anywhere inside a consumer's initialize() (an NPE reading an unrelated manager, a NumberFormatException on a value it computed) silently swaps the admin's tuned config for defaults on the next boot. The edited file survives as `<name>-old.yml` so this is recoverable rather than true data loss, which is why it is P1 and not P0; the server still comes up running default settings with only a warn line.
Fix: Only regenerate on parse/IO exceptions from the file itself; let unrelated initializer exceptions propagate or be logged without touching the file
Tests: -
Confidence: High

### [config-file #2] P1 — FileHandler.registerYamlFile swallows InvalidConfigurationException with no log at all
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/FileHandler.java:248-256
Observation: The YAML load is guarded by `catch (InvalidConfigurationException exception) { loaded = false; }` — no log call, unlike every sibling catch in the class which routes through logException/log.warn/log.error. An admin who leaves a tab character or a duplicate key in a config gets no message whatsoever; the handler simply reports not-loaded and every value silently falls back to defaults. The identical silent pattern repeats in createNewFile's post-regeneration reload at line 139, `catch (IOException | InvalidConfigurationException ignored) { loaded = false; }`. This is the archetypal admin-invisible failure the repo's own Result/Diagnostics rule exists to prevent.
Fix: Log the parse failure through logException/Diagnostics before setting loaded=false
Tests: -
Confidence: High

### [config-file #3] P1 — ConfigSerializer.scalarToJava does not strip digit-grouping underscores, so 10_000_000 degrades to a String
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/config/ConfigSerializer.java:172-189
Observation: NodeReader treats underscore digit grouping as a supported config convention — normalizeNumeric (NodeReader.java:55-67) strips `_` between digits so `10_000_000` parses, and the reader path uses it at line 231. ConfigSerializer's parallel path does not: the Tag.INT branch is `long parsed = Long.parseLong(value.trim());` and the Tag.FLOAT branch `return Double.parseDouble(value.trim());`, each with `catch (NumberFormatException e) { return value; }`. An underscore-grouped number therefore silently comes back as the String "10_000_000" instead of a number, so a consumer reading it as an int gets a type mismatch or its default, and a round trip through the serializer rewrites the value as a quoted string. Two supported spellings of the same config value behave differently depending on which reader path touches it.
Fix: Run the value through the same normalizeNumeric helper NodeReader uses before parseLong/parseDouble
Tests: -
Confidence: High

### [database-repos #1] P1 — RepositoryRegistry.canRegisterRepository inverts its Table check, defeating ordering and logging a false circular dependency
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/repository/RepositoryRegistry.java:376-386
Observation: The dependency check is backwards: `if (!tablesByName.containsKey(tableName)) { continue; }` skips the parameter when the table is absent, and then the code logs "depends on table {} which is not yet registered" and returns false for the case where the table IS present. sortRepositoriesByDependencies therefore refuses every repository whose Table dependency is already satisfied, makes no progress, and falls into its escape hatch at line 332 which logs "Circular dependency in repositories detected, registering remaining repositories in order" and appends the rest in declaration order. The net effect on every consumer boot is that table-dependency ordering is silently not applied, plus a misleading circular-dependency warning; FK-bearing schemas that need parent-before-child creation are then at the mercy of scan order.
Fix: Swap the branches so a missing table returns false and a present table continues the loop
Tests: -
Confidence: High

### [database-repos #2] P1 — DatabaseHelper caches getDatabase() at construction, so a helper built before connect() makes every runQueries a silent no-op
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/database/DatabaseHelper.java:19-24
Observation: The constructor snapshots the handler's current Database once: `this.database = databaseHandler.getDatabase();`. Every query path then uses that captured reference and opens with `if (database == null) return;` (line 47), so a helper constructed before the handler has resolved its database swallows every subsequent query silently — no exception, no log, no fault. The same staleness applies after the handler swaps its Database (the MySQL to SQLite fallback, or a schema switch), leaving the helper pointed at a closed instance. Because the failure mode is a quiet return on a data path rather than an error, an admin sees writes simply not happen.
Fix: Resolve the Database from the handler on each call instead of caching it in a final field
Tests: -
Confidence: High

### [database-repos #3] P1 — DatabaseManager.createDatabaseBackup matches existing rows on columns[0] only, so backups collapse rows on composite keys
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/database/DatabaseManager.java:158-176
Observation: The backup writer decides insert-vs-update purely from the first column: `Object[] row = config.select(columns[0] + " = ?", new Object[]{objects[0]}, new int[]{dataTypes[0]}, new String[]{"*"});` and then updates with the same single-column predicate. For any table whose primary key is composite, or whose PK simply is not the first column in the column array, distinct source rows share a columns[0] value and each one updates the previously written row instead of inserting. The backup silently ends up with fewer rows than the source and with fields from the last-written row, i.e. corrupted backup data that only surfaces when someone restores from it.
Fix: Match on the table's real primary-key column set from the schema rather than assuming columns[0] is unique
Tests: DatabaseManagerTest (startBackup_sqliteToSqlite_copiesData uses a single-column PK listed first, so it never exercises this)
Confidence: High

### [diagnostics-error #1] P1 — Diagnostics.install unconditionally overwrites the process-wide active hub, so the last consumer to boot steals every plugin's fault sink
Location: keystone-common/src/main/java/org/luckyraven/keystone/diagnostics/Diagnostics.java:118-121
Observation: The installer is `public static void install(Diagnostics diagnostics) { active = diagnostics; }` — a bare assignment to one JVM-wide static with no already-installed check and no warning. Keystone is loaded once and shared, so on a server running Gangland plus Bartizan or Oriel the second plugin to bootstrap silently replaces the first plugin's hub, and every later `Diagnostics.active()` call from the first plugin routes its faults into the second plugin's sink and fault table. The repo's own shared-classloader rule names exactly this pattern — a static singleton holding per-plugin state — as forbidden, and the consequence is misattributed and misrouted diagnostics on precisely the multi-consumer servers this library exists to serve.
Fix: Make install() reject or warn when a hub is already active, or key the active hub per owning plugin
Tests: DiagnosticsTest (resets the static between tests rather than asserting install-collision behaviour)
Confidence: High

### [diagnostics-error #2] P1 — PlayerInputInterceptor.channelRead swallows every exception from apply() with an empty catch
Location: keystone-common/src/main/java/org/luckyraven/keystone/nms/input/PlayerInputInterceptor.java:198-205
Observation: Every inbound player-input packet runs `try { apply(session, parsePacket(msg)); } catch (Exception ignored) { }` on the Netty IO thread. Any failure in a concrete subclass's apply() — a consumer's vehicle or jetpack input handling — or in parsePacket's reflection is discarded with no log, no fault and no counter, so the feature simply stops responding while the server looks healthy. This is the exact log-and-swallow shape the repo's Result/Diagnostics rule forbids, made worse by sitting on a per-packet hot path where an admin has no other signal.
Fix: Report through Diagnostics (rate-limited) instead of an empty catch, so a broken subscriber is visible
Tests: -
Confidence: High

### [diagnostics-error #3] P1 — PlayerInputInterceptor.ensureReflectionReady silently disables input parsing forever when the NMS packet class fails to resolve
Location: keystone-common/src/main/java/org/luckyraven/keystone/nms/input/PlayerInputInterceptor.java:96-103
Observation: Resolution is one-shot and its failure path is silent: `try { resolveAgainst(Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerInputPacket")); } catch (Exception ignored) { packetClass = null; } finally { reflectionReady = true; }`. Setting reflectionReady in the finally means the failure is cached permanently for the JVM, and channelRead's guard `if (reflectionReady && packetClass != null && ...)` then skips every packet for the rest of the server's uptime. On a Minecraft build that renames the packet class or its accessors, every consumer's input-driven feature goes dead at once with not a single line in the console explaining why.
Fix: Report a fault when resolution fails so the degraded state is visible instead of silently permanent
Tests: -
Confidence: High

### [diagnostics-error #4] P1 — ReflectivePacketAdapter.updateTitleThroughPackets lets NmsCache wiring failure escape as an uncaught IllegalStateException
Location: keystone-common/src/main/java/org/luckyraven/keystone/nms/internal/ReflectivePacketAdapter.java:135-152
Observation: The pre-1.20 title fallback calls `NmsCache cache = NmsCache.get();` inside a block guarded only by `catch (ReflectiveOperationException ex)`. NmsCache.build() wraps any reflective wiring failure in an IllegalStateException, which is unchecked and therefore sails straight through that catch to the caller. On a server in the supported 1.16.5-to-1.19 range where the reflective handles fail to wire, a routine title update throws instead of degrading, surfacing as a stack trace on whatever consumer path requested it. The adapter's whole purpose is to degrade rather than throw, so the narrow catch defeats its own contract.
Fix: Catch IllegalStateException alongside ReflectiveOperationException and degrade to the non-packet path
Tests: -
Confidence: High

### [hooks-integration #1] P0 — EconomyHandler.setAmount discards both Vault EconomyResponses, creating or destroying money when either leg fails
Location: keystone-hooks/src/main/java/org/luckyraven/keystone/economy/EconomyHandler.java:58-66
Observation: setAmount implements "set the balance" as a withdraw-everything-then-deposit-the-new-value pair and throws away both return values: `vaultEconomy.withdrawPlayer(offlinePlayer, vaultEconomy.getBalance(offlinePlayer)); vaultEconomy.depositPlayer(offlinePlayer, this.amount.doubleValue());`. Vault reports failure through the returned EconomyResponse rather than by throwing, so when the backing economy plugin refuses the withdraw — a frozen or bank-backed account, a plugin-side limit, a race with another transaction — the deposit still runs and the player keeps their old balance plus the new amount, creating money out of nothing. The mirror case destroys it: a successful withdraw followed by a rejected deposit leaves the account at zero. The pair is also non-atomic, so a server crash or exception between the two calls loses the whole balance, and every consumer that sets a balance through this class inherits the fault.
Fix: Check transactionSuccess() on the withdraw before depositing, and abort (or compensate) when either response fails
Tests: EconomyHandlerTest (userMode_setAmountWithdrawsEverythingThenDepositsTheNewBalance pins the call order without asserting response handling)
Confidence: High

### [hooks-integration #2] P1 — EconomyHandler.depositAmount accepts a negative delta, bypassing withdrawAmount's insufficient-funds guard
Location: keystone-hooks/src/main/java/org/luckyraven/keystone/economy/EconomyHandler.java:68-70
Observation: depositAmount is simply `setAmount(getAmount().add(Currency.of(delta)));` with no sign validation, while the sibling withdrawAmount explicitly guards with `if (normalised.compareTo(current) > 0) throw new EconomyException("Amount exceeding balance");`. Currency.of only rescales the value (`return raw.setScale(SCALE, ROUNDING_MODE);`) and never rejects a negative, so a mis-signed refund or penalty delta debits the account through the path that has no balance check and can drive the computed balance below zero. setAmount then withdraws everything and calls depositPlayer with a negative amount, which most Vault economies reject outright, leaving the player at zero with their original balance gone. Filed P1 rather than P0 because it takes a caller passing a negative delta, but the consequence is real money loss.
Fix: Reject negative deltas in depositAmount (throw EconomyException) so every debit routes through withdrawAmount's balance check
Tests: -
Confidence: High

### [module-loader #1] P1 — ModuleLoader.purgeStale lets one undeletable stale jar abort discovery of every module in the folder
Location: keystone-module/src/main/java/org/luckyraven/keystone/module/ModuleLoader.java:334-345
Observation: The first loop deletes each file in `.stale/` defensively, catching IOException per file and only warning. The second loop does not: `if (Files.deleteIfExists(jar)) { log.info(...); } Files.deleteIfExists(marker);` runs bare, and discover() wraps the whole purgeStale() call in a single try/catch that reports FAULT_FOLDER "Modules folder is not usable" and returns. On Windows a stale jar still held open by a previous run (the classloader handle problem this project already documents) throws IOException here, so one leftover file makes the host plugin load zero modules instead of skipping that one file. The asymmetry with the loop three lines above shows the per-file tolerance was the intent.
Fix: Wrap the marker loop's deleteIfExists calls in a per-file try/catch that logs, mirroring the stale-directory loop above it
Tests: -
Confidence: High

### [module-loader #2] P1 — ArtifactCoordinate.jarFileName builds a filename from unsanitized Maven coordinates, letting a repository escape the modules folder
Location: keystone-module/src/main/java/org/luckyraven/keystone/module/artifact/ArtifactCoordinate.java:49-52
Observation: jarFileName() is `requireVersion(); return artifactId + "-" + version + ".jar";` with no character validation, and ArtifactResolver.download() feeds it straight to the filesystem: `Path target = targetDirectory.resolve(coordinate.jarFileName());` (ArtifactResolver.java:100). For an update download the version half comes from the remote maven-metadata.xml via MavenMetadata, so the value is controlled by whatever repository the admin configured — a compromised or hostile mirror can return a version containing `../` (or an absolute path, which resolve() honours outright) and place the downloaded jar anywhere the server process can write. Filed P1 rather than P0 because it requires a hostile repository rather than firing on a normal update.
Fix: Reject coordinates containing path separators or "..", and verify the resolved target stays inside targetDirectory
Tests: -
Confidence: High

### [npc-system #1] P1 — NpcCombatDelegate.attackEntity never consults the targetFilter SPI that attack(Player) enforces
Location: keystone-npc/src/main/java/org/luckyraven/keystone/npc/NpcCombatDelegate.java:72-74
Observation: attack(Player) opens with `if (player.isDead() || !owner.targetFilter().isAttackable(player)) return;` but the entity-typed sibling opens with only `if (target.isDead()) return;` and never calls owner.targetFilter() anywhere in its body. NpcTargetFilter is the SPI through which a consumer expresses what its NPC may not hit — allied gang members, civilians, protected mobs — so any consumer path that routes through attackEntity rather than attack(Player) has its protection silently skipped. The NPC then damages entities the consumer explicitly marked unattackable, which is exactly the product decision the layering rules push out to the SPI.
Fix: Add the same owner.targetFilter().isAttackable(target) guard to attackEntity that attack(Player) already applies
Tests: -
Confidence: High

### [npc-system #2] P1 — AbstractNpc.destroy skips despawn and the onDespawn callback when rangedAttack.onDestroy throws
Location: keystone-npc/src/main/java/org/luckyraven/keystone/npc/AbstractNpc.java:160-175
Observation: destroy() runs `rangedAttack.onDestroy();` as the first statement inside the try, before the `if (npc.isSpawned())` block that fires the onDespawn consumer callback and `npc.despawn()`. A consumer's ranged-attack SPI implementation that throws there skips both, and the finally block only covers cleanupTransientState() and npc.destroy(). The onDespawn callback is documented one line above as the caller's chance to "release entity-keyed state (marks, registries, ...)", so a throwing SPI permanently leaks that consumer state while the NPC itself is still destroyed — an inconsistent half-teardown rather than a clean failure.
Fix: Wrap rangedAttack.onDestroy() in its own try/catch so a failing SPI cannot skip the despawn callback
Tests: AbstractNpcDestroyTest (happy path only)
Confidence: High

### [npc-system #3] P1 — NpcNavigationDelegate leaves ladderClimbActive set when the entity is transiently null, stalling navigation permanently
Location: keystone-npc/src/main/java/org/luckyraven/keystone/npc/NpcNavigationDelegate.java:414-426
Observation: updateNavigationProgress reads `LivingEntity entity = owner.getEntity(); if (entity == null) { return; }` and only afterwards tests `if (ladderClimbActive) { tickLadderClimb(); return; }`, so a null entity during a climb skips the tick that would normally end it and the flag stays true. That flag then disables all recovery: stopNavigation is `if (ladderClimbActive) return; owner.npc.getNavigator().cancelNavigation(); resetNavigationTracking();` (line 216-219), so it returns without cancelling anything. Citizens reporting a momentarily null entity — a chunk unload or a despawn/respawn cycle — therefore leaves that NPC unable to navigate for the rest of its life, and its gravity restoration in resetNavigationTracking never runs either.
Fix: Check and clear ladderClimbActive before the entity==null early return in updateNavigationProgress
Tests: -
Confidence: High

### [npc-system #4] P1 — EntitySpawner.findSpawnLocation loops forever on the main thread when getSpawnRadiusShrinkStep returns zero or negative
Location: keystone-npc/src/main/java/org/luckyraven/keystone/npc/entity/EntitySpawner.java:190-196
Observation: The phase-2 search is `for (double max = maxDist; max >= minDist; max -= shrinkStep)` where `double shrinkStep = config.getSpawnRadiusShrinkStep();` comes straight from the consumer's SpawnConfigProvider with no bounds check. A value of 0 never decrements max, and a negative value increases it, so the loop never terminates while running on the main server thread — the whole server hangs and the watchdog eventually kills it. The trigger is a single mistyped spawn-radius value in a consumer's NPC YAML, and nothing between the config read and the loop rejects it.
Fix: Validate the shrink step is greater than zero before the loop and fall back to a sane default
Tests: -
Confidence: High

### [scheduling-cooldowns #1] P0 — Timer.start after stop throws IllegalStateException because BukkitRunnable can only be scheduled once
Location: keystone-common/src/main/java/org/luckyraven/keystone/timer/Timer.java:49-66
Observation: Timer extends BukkitRunnable and start() schedules the instance itself: `if (async) this.bukkitTask = runTaskTimerAsynchronously(plugin, delay, period); else this.bukkitTask = runTaskTimer(plugin, delay, period);`. stop() then does `this.bukkitTask.cancel(); this.stopped.set(true); this.bukkitTask = null;`, which clears Keystone's own guard so a later start() proceeds — but Bukkit's BukkitRunnable.cancel() is only `Bukkit.getScheduler().cancelTask(getTaskId())` and never clears its internal task field, so the next schedule hits `checkNotYetScheduled()` and throws `IllegalStateException("Already scheduled as " + task.getTaskId())` (verified against the Spigot 1.16.5 API sources). Any stop-then-start on the same Timer, RepeatingTimer, CountdownTimer, CountupTimer or SequenceTimer therefore crashes, which is exactly what a managed reload does when it cancels timers and starts them again. Every existing timer test drives run() directly against a mocked isRunning() and never touches the real scheduler, so nothing catches it.
Fix: Stop extending BukkitRunnable and schedule a separate Runnable per start, so a Timer instance can be restarted
Tests: -
Confidence: High

### [scheduling-cooldowns #2] P1 — InMemoryCooldownService and PersistentCooldownService never evict entries for players who leave
Location: keystone-common/src/main/java/org/luckyraven/keystone/cooldown/InMemoryCooldownService.java:48-62
Observation: The backing maps are `ConcurrentHashMap<UUID, Map<String, Long>>` and the only removal is lazy, inside the lookup for that exact key: `if (expiry <= System.currentTimeMillis()) { byId.remove(id); return 0L; }`. A player who triggers a cooldown once and then logs out is never queried again, so their UUID and its inner map stay resident for the life of the server. On a busy server with a rotating player base this grows without bound, and the persistent subclass keeps the same shape in its cache, so the leak is in both implementations.
Fix: Evict a player's map on quit (or sweep expired entries periodically) instead of only on a repeat lookup of the same id
Tests: -
Confidence: High

### [scheduling-cooldowns #3] P1 — PersistentCooldownService performs its schema, purge and upsert JDBC calls synchronously on the calling thread
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/cooldown/PersistentCooldownService.java:110-125
Observation: start() calls ensureReady() — which on first use runs backend.applySchema(...), a DELETE purge and a full-table SELECT — and then persistStart(), an upsert, all inline on whatever thread called CooldownService.start(). Cooldowns are set from gameplay paths (a click, a command, an ability), which run on the main server thread, so every cooldown write blocks the tick for a database round trip and the very first one blocks for a schema apply plus a full table read. On MySQL over a network that is a visible stall on a normal path; the in-memory sibling has no such cost, so a consumer swapping implementations silently inherits main-thread I/O.
Fix: Move the persistence writes off the caller's thread (async task or write queue), keeping the in-memory update synchronous
Tests: -
Confidence: High

### [scheduling-cooldowns #4] P1 — PersistentCooldownService swallows every SQLException with a log.warn and continues as if persisted
Location: keystone-persistence/src/main/java/org/luckyraven/keystone/persistence/cooldown/PersistentCooldownService.java:186-188
Observation: ensureReady() ends with `catch (SQLException exception) { log.warn("Failed to initialise persistent cooldown store: {}", exception.toString(), exception); }` and persistStart() (lines 217-219) and persistClear() (lines 236-238) repeat the pattern, each continuing normally afterwards. The whole point of this implementation over the in-memory one is that cooldowns survive a restart, so a failing write means the persistence guarantee is quietly gone while the service reports success to its caller. The admin gets one warn line per failure and no fault in the diagnostics report, which is the log-and-swallow shape the repo's error rule forbids on a data path.
Fix: Report through Diagnostics and surface the degraded state rather than continuing silently
Tests: -
Confidence: High
