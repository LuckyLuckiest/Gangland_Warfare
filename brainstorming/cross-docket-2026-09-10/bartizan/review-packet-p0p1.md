# Bartizan P0/P1 findings for independent review (Bartizan repo: E:\Programming\java\Bartizan)

### [commands #1] P1 — The three give commands never bound the parsed amount, so a negative throws and a zero reports a false success
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/command/WeaponGiveCommand.java:104-117
Observation: giveWeapon takes the amount straight from `weaponAmount = Integer.parseInt(args[3])` with no range check and computes `int slots = (int) Math.ceil(amount / (double) maxStackSize);` then `ItemStack[] items = new ItemStack[slots];`. For an amount of -100 against a stack size of 64, slots is -1 and `new ItemStack[-1]` throws NegativeArraySizeException, which the command framework surfaces to the admin as a bare number. For an amount of 0 the array is empty, the loop never runs, and giveWeapon still returns true, so the admin is told the give succeeded while nothing entered the inventory. A very large amount drives an unbounded allocation instead. The identical pattern is duplicated in AmmunitionGiveCommand.java:91-104 and WearableGiveCommand.java:96-108.
Fix: Clamp the parsed amount to at least 1 and to a sane maximum right after Integer.parseInt, in all three give commands
Tests: -
Confidence: High

### [config-parsing #1] P1 — WeaponLoader logs a weapon parse failure at log.info, burying a broken config in startup chatter
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/file/WeaponLoader.java:29-37
Observation: The load callback is `try { weaponAddon.registerWeapon(ammunitionManager, fileHandler); } catch (InvalidConfigurationException exception) { log.info("There was a problem loading the weapon: {}", exception.getMessage()); }`. Every validation throw in the five weapon parsers — missing Selective_Fire, missing Projectile, missing Damage, an unresolvable Display_Item.Material — lands here and is reported at info level, indistinguishable from routine startup output. The weapon does correctly stay out of WeaponAddon's map so tab-completion no longer offers it, which is an improvement on the original Gangland report, but the admin gets no visible signal that a weapon silently vanished from the catalogue. Partially reproduces Gangland WP-31, log level only.
Fix: Log the caught InvalidConfigurationException at log.warn or log.error
Tests: -
Confidence: High

### [events #1] P1 — WeaponInteract's eight per-weapon state maps are never cleared on player quit, leaving timers running for an offline player
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/listener/WeaponInteract.java:80-120
Observation: WeaponInteract owns continuousFire, pressLockUntilTick, pressHoldState, releaseCallbacks, autoTasks, activeTasks, meleeCooldowns and lastMeleeSwingMs, all keyed by weapon UUID and all cleared only from onWeaponHeld on a weapon swap. WeaponQuitCleanupListener.onPlayerQuit reads only `ItemStack item = player.getInventory().getItemInMainHand();` and then calls stopReloading() and unScope(player, true), touching none of the maps. A player who disconnects mid-AUTO-fire or mid-throwable-charge leaves the FullAutoTask and RepeatingTimer entries running and calling Bukkit Player APIs against an offline Player until their own watchdog eventually times out. Was Gangland WP-9, quit path.
Fix: Extend WeaponQuitCleanupListener to stop and remove the quitting player's entries in autoTasks, activeTasks, continuousFire, pressHoldState and the rest
Tests: -
Confidence: High

### [events #2] P1 — Async press-hold watchdogs write the non-volatile WeaponData.shooting flag the main-thread interact handler also writes
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/listener/WeaponInteract.java:544-570
Observation: engagePressHoldWatchdog and shootFullAuto's AUTO watchdog both schedule a RepeatingTimer with `.start(true)` under the comment "Pure flag-flipping + map mutation, so safe to run async", and mutate `stillHeld.get().shooting = false;`. WeaponData declares `private boolean shooting;` with no volatile, and the same field is set true from the main thread at lines 396, 478, 527 and 653 during PlayerInteractEvent handling. The eight surrounding maps are all ConcurrentHashMap so there is no map corruption, but the flag itself has no memory barrier, so a release can be missed for a cycle and the press-lock either lingers or re-arms late. shootFullAuto's async watchdog additionally calls resetRecoilPattern(), mutating RecoilManager.playerPatternIndex concurrently with applyRecoil on the main thread.
Fix: Run these watchdogs with start(false) as handleBiologicalCharge already does, or make WeaponData.shooting an AtomicBoolean
Tests: -
Confidence: Medium

### [firing-actions #1] P1 — BiologicalAction.fireRay impact handler applies potion effects but never damages, so biological shots deal 0 HP
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/weapon/action/BiologicalAction.java:133-153
Observation: fire() computes `double damage = data.getBaseDamage() * level + flatBonus;` and passes it to fireRay, which sets `.baseDamage(damage)` but also supplies a custom `.impactHandler(event -> { if (event.getHitEntity() instanceof LivingEntity target) { for (PotionEffect effect : effects) target.addPotionEffect(effect); } })`. Verified in WeaponRaytracerImpl.java:426-437: a non-null impact handler runs and then `return;`s before the default `living.damage(event.getDamage(), shooter)` block at line 448, so the handler short-circuits damage entirely. Every charged biological shot applies only potion effects and zero health damage no matter what Base_Damage or charge level is configured. Was Gangland WP-3.
Fix: In the impactHandler call target.damage(damage, player) for LivingEntity hits as IncendiaryAction does, or drop the custom handler so the raytracer's default damage path runs
Tests: -
Confidence: High

### [firing-actions #2] P1 — ThrowableAction EXPLOSIVE detonation damages twice and passes Explosion_Radius as vanilla explosion power
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/weapon/action/ThrowableAction.java:213-247
Observation: detonate() calls `world.createExplosion(center.getX(), center.getY(), center.getZ(), (float) data.getExplosionRadius(), false, false, player);` — the trailing booleans suppress fire and block-breaking but NOT entity damage — then loops getNearbyEntities in the same radius and calls `target.damage(impactEvent.getDamage(), player)`. Anyone inside the blast takes vanilla explosion damage plus the full configured Explosion_Damage a second time. The fourth argument is explosion POWER, not radius: grenade.yml ships `Explosion_Radius: 4.0`, which is TNT-strength and reaches roughly 8 blocks, so entities 4-8 blocks out take vanilla damage the manual loop never intended to touch. Was Gangland WP-4.
Fix: Stop using createExplosion for damage (power 0 or a cosmetic-only burst) and let the manual WeaponRaytraceImpactEvent loop own the configured damage and radius
Tests: -
Confidence: High

### [firing-actions #3] P1 — MeleeAction and ThrowableAction never call weapon.consumeShot(), so configured magazines never deplete
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/weapon/action/MeleeAction.java:55-63
Observation: MeleeAction.activate() guards with `if (weapon.getReloadData() != null && weapon.isMagazineEmpty())` but never calls consumeShot() anywhere in the class; ThrowableAction.activate() never references ammunitionData, reloadData or consumeShot at all and only decrements the held ItemStack. A repo-wide grep for `consumeShot()` returns only NpcWeaponControllerImpl, BiologicalAction, GunAction and IncendiaryAction. Any melee or throwable weapon authored with Ammunition:/Reload: sections has unlimited uses, because the isMagazineEmpty() guard reads a currentMagCapacity nothing ever decrements. Was Gangland WP-14 (melee/throwable half).
Fix: Call weapon.consumeShot() in MeleeAction.activate() and ThrowableAction.activate() gated on getAmmunitionData()/isMagazineEmpty() as the gun paths do
Tests: WeaponConsumeShotTest (javadoc states it does not claim melee/throwable consume ammo in real play)
Confidence: High

### [firing-actions #4] P1 — PluginFireRegistry.onShutdown() clears the tracked set without reverting the FIRE blocks it placed
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/fire/PluginFireRegistry.java:72-75
Observation: The whole shutdown hook is `@Override public void onShutdown() { tracked.clear(); }`. Fire blocks placed by IncendiaryAction/ThrowableAction are reverted by per-block scheduled tasks, and those tasks are cancelled when the plugin disables, so any fire still burning at server stop or /reload is left in the world as a real block. Because tracked.clear() also erases the bookkeeping, PluginFireProtectionListener can no longer recognise those blocks as plugin-placed on the next start, so leftover fire behaves as vanilla fire and can spread and burn player structures. Was Gangland WP-38.
Fix: Iterate the tracked BlockVectors per world and reset each still-FIRE block to AIR before clearing the map
Tests: -
Confidence: High

### [nucleus #1] P1 — Bartizan.onDisable calls the test-only PacketBridge.reset, wiping the server-global packet adapter every other Keystone plugin shares
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/Bartizan.java:47-51
Observation: onDisable runs `getServer().getServicesManager().unregisterAll(this); PacketBridge.reset();` before tearing down the context. Keystone's PacketBridge holds `private static volatile PacketAdapter adapter = NoOpAdapter.INSTANCE;` — one server-global instance shared by every consumer plugin on the shared Keystone classloader — and its reset() carries the javadoc "Reset to the no-op fallback. Test-only." Disabling or reloading Bartizan therefore silently downgrades recoil and packet handling to a no-op for every other Keystone-powered plugin still running, with no log line. The same path also runs when bootstrap fails, since onEnable's catch calls disablePlugin(this).
Fix: Remove the PacketBridge.reset() call from onDisable, or replace it with a scoped uninstall that only reverts an adapter Bartizan itself installed
Tests: -
Confidence: High

### [persistence #1] P1 — WeaponRepository.deleteAll wraps a backend call in an unused legacy Database and bypasses Diagnostics routing
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/database/WeaponRepository.java:38-41
Observation: The whole method is `DatabaseHelper helper = new DatabaseHelper(getPlugin(), getDatabaseHandler()); helper.runQueriesAsync(database -> tableBackend().delete(null));` — the lambda ignores the injected legacy `database` parameter entirely and calls tableBackend(), the HikariCP DatabaseBackend path, so DatabaseHelper's synchronized block and connect/disconnect bracketing guard nothing that is actually used. DatabaseHelper.runQueries opens with `if (database == null) return;`, so a null legacy handle makes the entire table delete a silent no-op with no logging. Any SQLException from the backend delete is swallowed by DatabaseHelper's generic warn and never reaches Diagnostics.active(), unlike every other mutation path in AbstractRepository.
Fix: Route deleteAll through AbstractRepository's own backend-async path so failures reach reportBackendFailure like save, saveAll and delete
Tests: -
Confidence: High

### [raytrace #1] P0 — Weapon block breaking calls block.setType(AIR) without ever firing BlockBreakEvent, bypassing every protection plugin
Location: bartizan-api/src/main/java/org/luckyraven/bartizan/api/weapon/modifiers/BlockDamageManager.java:206-218
Observation: BlockDamageManager removes blocks with a bare `block.setType(Material.AIR);` at two sites, reached from WeaponRaytracerImpl.applyBlockBreak (line 500-508) on every raytraced block hit that matches a Break_Blocks modifier. A repo-wide grep for BlockBreakEvent finds only an import and a listener in WeaponInteract — nothing in the plugin ever fires one. Five shipped weapons configure Break_Blocks (rifle ships `- GLASS-3`, `- ICE-5`, `- TERRACOTTA-10`, plus shotgun, minigun, steyr_aug, golden_ak47), so out of the box any player holding a rifle can destroy glass, ice and terracotta inside a WorldGuard or GriefPrevention claim with no plugin able to observe or veto it.
Fix: Fire a cancellable BlockBreakEvent (or the region-plugin equivalent) in BlockDamageManager before removing the block and honour its cancellation
Tests: -
Confidence: High

### [raytrace #2] P0 — Cosmetic ROCKET Fireball is spawned without setYield(0), so vanilla explosion physics fire alongside the configured blast
Location: bartizan-api/src/main/java/org/luckyraven/bartizan/api/raytrace/WeaponVisualSpawner.java:49-64
Observation: WeaponShooting.fireSlow picks `Class<? extends Projectile> visualClass = type == ProjectileType.ROCKET ? Fireball.class : Firework.class;` and spawnCosmetic does only `projectile.setSilent(true); projectile.setGravity(false); projectile.setShooter(shooter); projectile.setVelocity(velocity);` before returning. A grep for setYield, setIsIncendiary, EntityExplodeEvent and ExplosionPrimeEvent across both modules returns nothing, so the Fireball keeps its default yield and explodes on contact through vanilla physics with real block destruction and entity damage. That fires in addition to SteppedProjectileTask's own configured fireExplosion, so every rocket shot produces one uncontrolled vanilla explosion that ignores Explosion_Radius and Explosion_Damage entirely, contradicting the spawner's javadoc that cosmetic entities never drive damage logic.
Fix: Call setYield(0f) and setIsIncendiary(false) on the spawned projectile inside WeaponVisualSpawner.spawnCosmetic
Tests: -
Confidence: High

### [raytrace #3] P1 — CombatEligibility.canBeHit is never consulted for the entity being shot, only for the shooter and reloader
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/raytrace/WeaponRaytracerImpl.java:291-296
Observation: advanceRay's entity predicate is `e -> e != shooter && !(e instanceof ItemFrame || e instanceof ArmorStand) && request.getEntityFilter().test(e)`, and RaytraceRequest.Builder defaults `entityFilter = e -> true` (RaytraceRequest.java:69), which WeaponShooting never overrides. All six canBeHit call sites in the repo gate the acting player: WeaponInteract.java:147 and :224 gate the shooter, InstantReload and NumberedReload gate the reloader, and WiringConfig.java:162 only builds the holder bean. CombatEligibility's javadoc says it replaces Gangland's DownedPlayerRegistry and returns false for a player who is downed or otherwise protected, so the contract's primary purpose — making those players un-shootable — is not implemented on the damage path.
Fix: Gate the raytracer's entity filter on CombatEligibility.resolve().canBeHit(player) for Player targets, in WeaponShooting or as the RaytraceRequest default
Tests: -
Confidence: High

### [weapon-model #1] P1 — Weapon.modifiersData is null for any weapon YAML without a Modifiers section and is dereferenced unguarded at 15 sites
Location: bartizan-api/src/main/java/org/luckyraven/bartizan/api/weapon/Weapon.java:59-59
Observation: `private ModifiersData modifiersData;` has no initialiser, and ModifiersSectionParser.apply() opens with `MappingNode modifiersSection = root.get("Modifiers").asMapping().orNull(); if (modifiersSection == null) return;` so a weapon YAML with no `Modifiers:` section leaves the field null. Fifteen call sites then dereference it with no guard, including `ProjectileState.canPenetrateBlock/canPenetrateEntity/canRicochet` (ProjectileState.java:66-93), `WeaponRaytracerImpl.java:501,515,555`, `ModifierHandler.java:96,121` and the flat-damage lookup in all five action classes. All 26 shipped weapon YAMLs carry a Modifiers section, so this is an authoring trap rather than a default-config break, but a server author writing an ordinary weapon with no modifiers gets an NPE on every shot that reaches these paths. Was Gangland WP-7.
Fix: Initialise the field as `private ModifiersData modifiersData = new ModifiersData();` so every caller sees an empty, non-null ModifiersData
Tests: ProjectileStateTest.canPenetrateAndRicochet_nullModifiersData_throwsNpe (pins today's NPE)
Confidence: High

### [weapon-model #2] P1 — SpreadManager.checkSpreadReset compares a tick-authored Time value against a millisecond delta
Location: bartizan-api/src/main/java/org/luckyraven/bartizan/api/weapon/spread/SpreadManager.java:66-75
Observation: checkSpreadReset computes `long timeSinceLastShot = currentTime - lastShotTime;` from System.currentTimeMillis() and compares it with `if (timeSinceLastShot >= spreadData.getResetTime())`. The YAML authors that value in ticks — rifle.yml ships `Time: 5` under a comment reading "The time before the spread value is reset", meaning 5 ticks or 250ms. Read as 5 milliseconds, any real firing cadence exceeds it, so currentSpread is reset to Starting_Spread on effectively every shot and spread never accumulates toward its configured bounds. Was Gangland WP-8.
Fix: Multiply spreadData.getResetTime() by 50 before the comparison, or track lastShotTime in ticks
Tests: SpreadManagerTest.applySpread_resetTimeInTicks_resetsFarEarlierThanIntended (pins today's behaviour)
Confidence: High

### [weapon-model #3] P1 — ReloadType.amount is a mutable field on a JVM-wide enum constant, shared by every weapon of that reload type
Location: bartizan-api/src/main/java/org/luckyraven/bartizan/api/weapon/reload/ReloadType.java:11-32
Observation: The enum declares `@Setter private int amount;` on constants INSTANT/ONE/NUM, which are singletons, and `createInstance` reads it via `case ONE, NUM -> new NumberedReload(weapon, ammunition, amount);`. Each weapon parser calls ReloadType.NUM.setAmount(...) at load time, so the last weapon parsed wins and every weapon of that reload type constructed afterwards reloads with that weapon's Reload_Amount. Two weapons configured with `num` reload and different Reload_Amount values silently reload by the same wrong number of rounds. Was Gangland WP-16.
Fix: Move Reload_Amount onto ReloadData per weapon and pass it into createInstance explicitly instead of reading enum state
Tests: TypeEnumParsingTest.reloadType_amountIsSharedMutableState (pins today's behaviour)
Confidence: High

### [weapon-model #4] P1 — WeaponService.weapons map is never pruned per player; only a full clear() on bean reload removes entries
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/weapon/WeaponService.java:32-32
Observation: `private final Map<UUID, Weapon> weapons;` only ever shrinks through `public void clear() { weapons.clear(); }`, whose sole caller is the WeaponManager.onClear() bean-reload hook — there is no removal tied to a player quitting, dying, or the item being destroyed. Every `/bartizan weapon give` registers a permanent entry, and WeaponManager.initialize() wires `repository.setDataSupplier(() -> getWeapons().values())`, so the map is also what gets persisted. The map therefore grows for the whole server uptime between reloads; the backing table is only bounded because WeaponDataCleanupTask calls repo.deleteAll() on a 30-day schedule. Was Gangland WP-9.
Fix: Remove a player's minted weapon uuids on PlayerQuitEvent and on item destruction, or hold them in a TTL/weak-keyed store
Tests: -
Confidence: High

### [weapon-model #6] P1 — mintUuid gives every throwable of a type one deterministic UUID, so all players share one press-lock and one registry entry
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/weapon/WeaponService.java:225-234
Observation: mintUuid returns `UUID.nameUUIDFromBytes(("throwable:" + type)...)` for every THROWABLE-category weapon regardless of caller, documented as intentional so identical throwables stack in the inventory, and the give path then runs `weapons.put(finalUuid, finalWeapon)` with that shared uuid. Two consequences follow. Giving the same throwable type to a second player overwrites the first player's registered Weapon object at that key in the shared map. Worse, WeaponInteract.handleThrowablePress gates on isPressGated(weaponUuid) and engagePressHoldWatchdog(weaponUuid, ...) using that same shared uuid, so one player throwing a grenade press-locks every other online player holding that grenade type until the watchdog clears. Was Gangland WP-32.
Fix: Key registered throwable instances and the WeaponInteract press maps by player plus type, keeping the deterministic uuid only for createTransientWeapon's inventory stacking
Tests: WeaponServiceTest.createTransientWeapon_throwable_usesDeterministicUuid (covers only the unregistered transient path)
Confidence: High

### [wearables #1] P0 — WearableEquipListener only sees InventoryClickEvent, so right-click and hotbar-swap equip bypass the wearable permission
Location: bartizan-plugin/src/main/java/org/luckyraven/bartizan/listener/wearable/WearableEquipListener.java:39-92
Observation: The class declares exactly one handler, `onArmorEquip(InventoryClickEvent event)`, and resolveArmorBeingEquipped only returns an item for an ARMOR-slot click whose action is `PLACE_ALL || PLACE_ONE || PLACE_SOME || SWAP_WITH_CURSOR`, or for a shift-click; every other case hits `return null;`. Right-clicking an armor piece held in hand — the ordinary vanilla way to equip armor — fires PlayerInteractEvent and never an InventoryClickEvent, and pressing a hotbar number key over an armor slot produces action HOTBAR_SWAP, which the whitelist omits. Neither path ever reaches `if (permission != null && !permission.isEmpty() && !player.hasPermission(permission))`, so any player can wear a permission-gated wearable by right-clicking it on, defeating bartizan.wearables.<key> entirely.
Fix: Add a PlayerInteractEvent handler for right-click equip, add HOTBAR_SWAP to the recognised armor-slot actions, and cover BlockDispenseArmorEvent
Tests: -
Confidence: High
