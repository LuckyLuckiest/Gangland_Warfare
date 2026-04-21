package me.luckyraven.weapon.listener;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.core.autowire.AutowireTarget;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.downed.DownedPlayerRegistry;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.core.timer.RepeatingTimer;
import me.luckyraven.core.timer.SequenceTimer;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.dto.ScopeData;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.types.biological.BiologicalAction;
import me.luckyraven.weapon.types.biological.BiologicalWeapon;
import me.luckyraven.weapon.types.gun.FullAutoTask;
import me.luckyraven.weapon.types.gun.GunAction;
import me.luckyraven.weapon.types.gun.GunWeapon;
import me.luckyraven.weapon.types.incendiary.IncendiaryAction;
import me.luckyraven.weapon.types.incendiary.IncendiaryWeapon;
import me.luckyraven.weapon.types.melee.MeleeAction;
import me.luckyraven.weapon.types.melee.MeleeWeapon;
import me.luckyraven.weapon.types.throwable.ThrowableAction;
import me.luckyraven.weapon.types.throwable.ThrowableWeapon;
import me.luckyraven.weapon.util.EmptyMagSoundGate;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ListenerHandler
@AutowireTarget({WeaponService.class, RecoilCompatibility.class, WeaponRaytracer.class})
public class WeaponInteract implements Listener {

	/**
	 * Floor on the press-lock window, in ticks. Slightly larger than the AUTO watchdog's 3-tick idle window so that
	 * very fast-cooldown SINGLE/BURST weapons still swallow the entire packet stream Spigot fires while RMB is held.
	 */
	private static final long MIN_PRESS_LOCK_TICKS = 4L;

	/**
	 * Length of one Minecraft tick in real-time milliseconds. Used to convert tick-denominated cooldowns to wall-clock
	 * deadlines for the press lock map (Spigot has no portable {@code Server#getCurrentTick}).
	 */
	private static final long MILLIS_PER_TICK = 50L;

	/**
	 * Dedup window for the two events Spigot delivers when a player left-clicks an entity with a melee weapon: the
	 * USE_ENTITY/ATTACK packet fires {@link EntityDamageByEntityEvent} and the companion swing-arm packet fires
	 * {@link PlayerInteractEvent} with {@code LEFT_CLICK_AIR}. Both reach this listener and would otherwise both call
	 * {@link MeleeAction#activate(Player)}, doubling the damage and the resulting kill credit. 150ms (≈3 ticks) is
	 * comfortably wider than the worst-case packet-delivery split between the two events while staying tight enough not
	 * to throttle real follow-up swings, which are gated by the configured weapon cooldown anyway.
	 */
	private static final long MELEE_DEDUP_WINDOW_MS = 150L;

	private final JavaPlugin          plugin;
	private final WeaponService       weaponService;
	private final RecoilCompatibility recoilCompatibility;
	private final WeaponRaytracer     raytracer;

	private final Map<UUID, AtomicReference<WeaponData>> continuousFire;
	/**
	 * SINGLE/BURST per-shot cooldown gate: maps weapon UUID to the wall-clock millisecond at which the next RMB press
	 * becomes eligible to fire. While the current time is below this value, fresh RMB presses are dropped — this
	 * prevents click-spam from outpacing the weapon's natural fire rate even when the player release-and-re-presses RMB
	 * rapidly. Cleared on weapon swap.
	 *
	 * <p>This is the cooldown gate only. Held-RMB suppression (one-shot-per-press) is handled separately by
	 * {@link #pressHoldState}; without that watchdog, the cooldown gate would lapse mid-hold and the next Spigot
	 * held-RMB event would fire another shot.
	 */
	private final Map<UUID, Long>                        pressLockUntilTick;
	/**
	 * SINGLE/BURST held-trigger gate. After a SINGLE/BURST shot fires, an entry is inserted here for the weapon and a
	 * release-detection watchdog is scheduled. While the entry exists, all incoming RMB events for the weapon are
	 * dropped — this is what enforces one-shot-per-press despite Spigot's repeated PlayerInteractEvent stream while RMB
	 * is held. The watchdog removes the entry once it observes a quiet event window (one full
	 * {@link #MIN_PRESS_LOCK_TICKS}-tick cycle without the {@link WeaponData#shooting} flag being refreshed by an
	 * incoming event), which signals that RMB has actually been released and the trigger should be re-armed for the
	 * next press.
	 *
	 * <p>This is orthogonal to {@link #pressLockUntilTick}: the cooldown gate enforces the weapon's natural fire rate
	 * across separate presses, while this map enforces "one shot per press" across the lifetime of a single hold.
	 */
	private final Map<UUID, AtomicReference<WeaponData>> pressHoldState;
	/**
	 * Per-weapon release callbacks invoked by the {@link #continuousFire} watchdog when it detects RMB has been
	 * released. Used by charge-then-release weapons (biological) to fire once the player lets go of RMB.
	 */
	private final Map<UUID, Runnable>                    releaseCallbacks;
	private final Map<UUID, FullAutoTask>                autoTasks;
	private final Map<UUID, RepeatingTimer>              activeTasks;
	private final Map<UUID, Long>                        meleeCooldowns;
	/**
	 * Per-weapon timestamp of the last melee swing that was actually fired, in milliseconds. Used to dedup the
	 * companion {@link PlayerInteractEvent}/{@link EntityDamageByEntityEvent} pair Spigot delivers for a single
	 * left-click on an entity — the two events can land in the same tick or in adjacent ticks depending on packet
	 * order, so a tick-based dedup window is unreliable. Anything within {@link #MELEE_DEDUP_WINDOW_MS} of the last
	 * recorded swing is treated as the second half of the same click and dropped.
	 */
	private final Map<UUID, Long>                        lastMeleeSwingMs;

	public WeaponInteract(JavaPlugin plugin, WeaponService weaponService, RecoilCompatibility recoilCompatibility,
	                      WeaponRaytracer raytracer) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.recoilCompatibility = recoilCompatibility;
		this.raytracer           = raytracer;
		this.continuousFire      = new ConcurrentHashMap<>();
		this.pressLockUntilTick  = new ConcurrentHashMap<>();
		this.pressHoldState      = new ConcurrentHashMap<>();
		this.releaseCallbacks    = new ConcurrentHashMap<>();
		this.autoTasks           = new ConcurrentHashMap<>();
		this.activeTasks         = new ConcurrentHashMap<>();
		this.meleeCooldowns      = new ConcurrentHashMap<>();
		this.lastMeleeSwingMs    = new ConcurrentHashMap<>();
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player = event.getPlayer();
		ItemStack item   = event.getItem();
		Weapon    weapon = weaponService.validateAndGetWeapon(player, item);

		if (weapon == null) return;

		if (player.isDead() || DownedPlayerRegistry.isDowned(player.getUniqueId())) return;

		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR ||
		                     event.getAction() == Action.RIGHT_CLICK_BLOCK;

		// scope toggle for any weapon type that has a scope configured
		ScopeData scopeData     = weapon.getScopeData();
		boolean   validateScope = true;
		if (scopeData != null) {
			validateScope = scopeData.getLevel() > 0;
		}

		if (leftClick && !player.isSneaking() && validateScope && !weapon.isReloading()) {
			event.setUseInteractedBlock(Event.Result.DENY);
			event.setUseItemInHand(Event.Result.DENY);

			if (scopeData != null && !scopeData.isScoped()) weapon.scope(player, true);
			else weapon.unScope(player, true);

			SoundConfiguration.playSounds(player, weapon.getSoundData().getScopeCustom(),
			                              weapon.getSoundData().getScopeDefault());
			return;
		}

		// dispatch non-GUN types before any gun-specific logic
		if (!(weapon instanceof GunWeapon gunWeapon)) {
			handleNonGunInteract(event, player, weapon, leftClick, rightClick);
			return;
		}

		// no interruption while the weapon is reloading
		if (gunWeapon.isReloading()) {
			event.setCancelled(true);
			return;
		}

		if (!rightClick) return;

		// cancel block interaction
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);

		SelectiveFire selectiveFire = gunWeapon.getCurrentSelectiveFire();


		if (selectiveFire == SelectiveFire.AUTO) {
			// handle the AUTO mode with full auto task
			shootFullAuto(gunWeapon, player, item);
		} else {
			// handle the BURST and SINGLE modes
			shootOtherModes(gunWeapon, player);
		}
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!weaponService.isWeapon(event.getPlayer().getInventory().getItemInMainHand())) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (!weaponService.isWeapon(event.getPlayer().getInventory().getItemInMainHand())) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onPlayerInteractWithEntity(PlayerInteractEntityEvent event) {
		Player    player = event.getPlayer();
		ItemStack item   = player.getInventory().getItemInMainHand();

		if (!weaponService.isWeapon(item)) return;
		event.setCancelled(true);

		if (player.isDead() || DownedPlayerRegistry.isDowned(player.getUniqueId())) return;

		Weapon weapon = weaponService.validateAndGetWeapon(player, item);
		if (weapon == null) return;

		// non-GUN types: no right-click-on-entity behavior
		if (!(weapon instanceof GunWeapon gunWeapon)) return;

		if (gunWeapon.isReloading()) {
			return;
		}

		// ignore the actions since this event is for right click interactions with entity

		SelectiveFire selectiveFire = gunWeapon.getCurrentSelectiveFire();

		if (selectiveFire == SelectiveFire.AUTO) {
			shootFullAuto(gunWeapon, player, item);
		} else {
			shootOtherModes(gunWeapon, player);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDamage(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player player)) return;

		// Drain the per-action pendingDamage sets first, regardless of which guard returns below.
		// MeleeAction adds its target UUID before calling target.damage(...) inside the raytracer's
		// impactHandler; the inner re-fired event then short-circuits at the raytracer flag check
		// without ever reaching a removal call below, leaving the UUID stranded in the static set
		// and consuming the next legitimate hit on that entity. Removing here ensures the sets
		// always drain in lock-step with the inner event they were added for.
		UUID    targetUuid         = event.getEntity().getUniqueId();
		boolean wasMeleeRefire     = MeleeAction.pendingDamage.remove(targetUuid);
		boolean wasThrowableRefire = ThrowableAction.pendingDamage.remove(targetUuid);
		boolean wasIncendRefire    = IncendiaryAction.pendingDamage.remove(targetUuid);

		// Allow damage that was applied programmatically by the unified raytracer (gun hitscan,
		// stepped slow projectiles, etc.). Without this guard the raytracer's living.damage(...)
		// call would be cancelled below as if it were a vanilla fist punch with a gun in hand.
		if (WeaponRaytracer.isRaytraceDamageInProgress()) return;

		// Same allowance for damage applied programmatically by MeleeAction / ThrowableAction /
		// IncendiaryAction themselves outside the raytracer flag window.
		if (wasMeleeRefire || wasThrowableRefire || wasIncendRefire) return;

		ItemStack item = player.getInventory().getItemInMainHand();
		if (!weaponService.isWeapon(item)) return;

		// For melee weapons: cancel the vanilla attack and trigger MeleeAction directly.
		// When the player clicks on an entity the client sends an attack-entity packet, which
		// fires EntityDamageByEntityEvent but does NOT always fire PlayerInteractEvent.
		// Running MeleeAction here ensures damage is applied regardless of which events arrive.
		// tryClaimMeleeSwing prevents double-damage when the companion PlayerInteractEvent also
		// fires for the same click (see MELEE_DEDUP_WINDOW_MS).
		Weapon weapon = weaponService.validateAndGetWeapon(player, item);
		if (weapon instanceof MeleeWeapon melee) {
			event.setCancelled(true);
			if (tryClaimMeleeSwing(melee.getUuid())) {
				boolean hit = new MeleeAction(melee, recoilCompatibility, raytracer, meleeCooldowns).activate(player);
				if (hit) melee.applyOnHitDurability(player, player.getInventory().getHeldItemSlot());
			}
			return;
		}

		// cancel the default Minecraft attack damage for all other weapon types.
		event.setCancelled(true);
	}

	@EventHandler
	public void onWeaponHeld(PlayerItemHeldEvent event) {
		// check if it was a weapon
		Player    player = event.getPlayer();
		ItemStack item   = player.getInventory().getItem(event.getPreviousSlot());
		Weapon    weapon = weaponService.validateAndGetWeapon(player, item);

		if (weapon == null) return;

		UUID weaponUuid = weapon.getUuid();

		// unscoped and reset recoil for any weapon type
		weapon.unScope(player, true);
		weapon.getRecoil().resetRecoilPattern();

		// drop the SINGLE/BURST press lock and held-trigger gate so the new selection starts on a clean trigger
		// (applies to both gun and incendiary weapons, both of which share these maps)
		pressLockUntilTick.remove(weaponUuid);
		pressHoldState.remove(weaponUuid);

		// drop the melee dedup timestamp so swapping weapons doesn't carry stale gating across selections
		lastMeleeSwingMs.remove(weaponUuid);

		if (weapon instanceof GunWeapon) {
			// cancel any active auto fire
			FullAutoTask autoTask = autoTasks.get(weaponUuid);
			if (autoTask != null) {
				autoTask.stop();
			}
		}

		// cancel active incendiary / biological tasks
		RepeatingTimer activeTask = activeTasks.remove(weaponUuid);
		if (activeTask != null) {
			activeTask.stop();
		}

		// drop any pending biological release callback so the charge dies with the swap
		releaseCallbacks.remove(weaponUuid);
		continuousFire.remove(weaponUuid);
	}

	/**
	 * Records that a melee swing for {@code weaponUuid} is being processed and reports whether the caller should
	 * actually fire it. Returns {@code false} if a swing for this weapon was already claimed within the
	 * {@link #MELEE_DEDUP_WINDOW_MS} window — that is the second half of the same left-click being delivered via the
	 * companion {@link PlayerInteractEvent}/{@link EntityDamageByEntityEvent}, and we must not fire it twice.
	 */
	private boolean tryClaimMeleeSwing(UUID weaponUuid) {
		long now  = System.currentTimeMillis();
		Long last = lastMeleeSwingMs.get(weaponUuid);
		if (last != null && now - last < MELEE_DEDUP_WINDOW_MS) {
			return false;
		}
		lastMeleeSwingMs.put(weaponUuid, now);
		return true;
	}

	private void handleNonGunInteract(PlayerInteractEvent event, Player player, Weapon weapon, boolean leftClick,
	                                  boolean rightClick) {
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);

		// block all actions while reloading
		if (weapon.isReloading()) return;

		switch (weapon) {
			case ThrowableWeapon throwable -> {
				if (!rightClick) break;
				handleThrowablePress(throwable, player);
			}
			case MeleeWeapon melee -> {
				if (leftClick) {
					if (tryClaimMeleeSwing(melee.getUuid())) {
						boolean hit = new MeleeAction(melee, recoilCompatibility, raytracer, meleeCooldowns).activate(
								player);
						if (hit) melee.applyOnHitDurability(player, player.getInventory().getHeldItemSlot());
					}
				}
			}
			case IncendiaryWeapon incendiary -> {
				if (!rightClick) break;

				IncendiaryAction action = new IncendiaryAction(plugin, weaponService, incendiary, recoilCompatibility,
				                                               raytracer);

				SelectiveFire mode = incendiary.getCurrentSelectiveFire();
				if (mode == SelectiveFire.AUTO) {
					handleIncendiaryAuto(incendiary, action, player);
				} else {
					handleIncendiaryPress(incendiary, action, player);
				}
			}
			case BiologicalWeapon biological -> {
				if (!rightClick) break;
				handleBiologicalCharge(biological, player);
			}
			default -> { }
		}
	}

	/**
	 * Charge-then-release trigger for biological weapons. The first RMB press starts the charge timer; subsequent RMB
	 * presses (Spigot fires them while RMB is held) keep the {@link WeaponData#shooting} flag refreshed. When the
	 * watchdog detects the player has released RMB, it invokes the release callback registered here, which calls
	 * {@link BiologicalAction#getReleaseCallback(Player)} to fire the charged shot.
	 */
	private void handleBiologicalCharge(BiologicalWeapon weapon, Player player) {
		UUID weaponUuid = weapon.getUuid();

		AtomicReference<WeaponData> existing = continuousFire.get(weaponUuid);
		if (existing != null) {
			existing.get().shooting = true;
			return;
		}

		BiologicalAction action = new BiologicalAction(plugin, weapon, recoilCompatibility, raytracer, activeTasks);
		if (!action.start(player)) return;

		WeaponData freshWeaponData = new WeaponData();
		freshWeaponData.shooting = true;
		AtomicReference<WeaponData> ref = new AtomicReference<>(freshWeaponData);
		continuousFire.put(weaponUuid, ref);

		releaseCallbacks.put(weaponUuid, action.getReleaseCallback(player));

		// Release-detection watchdog. Must be synchronous: when the player releases RMB, this fires the release
		// callback, which raytracer-calls — getNearbyEntities is main-thread only.
		new RepeatingTimer(plugin, 4L, time -> {
			AtomicReference<WeaponData> stillShooting = continuousFire.get(weaponUuid);
			if (stillShooting == null) {
				time.stop();
				releaseCallbacks.remove(weaponUuid);
				return;
			}
			if (!stillShooting.get().shooting) {
				time.stop();
				continuousFire.remove(weaponUuid);
				Runnable callback = releaseCallbacks.remove(weaponUuid);
				if (callback != null) callback.run();
				return;
			}
			stillShooting.get().shooting = false;
		}).start(false);
	}

	/**
	 * Single-press trigger for throwable weapons. Mirrors {@link #shootOtherModes(GunWeapon, Player)} and
	 * {@link #handleIncendiaryPress}: one throw per RMB press, gated by the same per-weapon held-trigger watchdog plus
	 * the cooldown gate. Throwables don't expose a configured per-shot cooldown, so the lock window is always
	 * {@link #MIN_PRESS_LOCK_TICKS} — without this gate, holding RMB on a grenade chain-fires throws every time Spigot
	 * resends a held-RMB PlayerInteractEvent.
	 */
	private void handleThrowablePress(ThrowableWeapon weapon, Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (isPressGated(weaponUuid)) return;

		engagePressHoldWatchdog(weaponUuid, MIN_PRESS_LOCK_TICKS);

		new ThrowableAction(plugin, weapon, recoilCompatibility).activate(player);
	}

	/**
	 * SINGLE/BURST press-lock trigger for incendiary weapons. Mirrors {@link #shootOtherModes(GunWeapon, Player)}: one
	 * cone burst per RMB press, gated by the same per-weapon held-trigger watchdog plus the cooldown gate. The lock
	 * window is the larger of the incendiary's tick rate and {@link #MIN_PRESS_LOCK_TICKS}.
	 */
	private void handleIncendiaryPress(IncendiaryWeapon weapon, IncendiaryAction action, Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (isPressGated(weaponUuid)) return;

		long lockTicks = Math.max(weapon.getIncendiaryData().getTickRate(), MIN_PRESS_LOCK_TICKS);
		engagePressHoldWatchdog(weaponUuid, lockTicks);

		action.fireOnce(player);
	}

	/**
	 * AUTO-mode trigger for incendiary weapons. Spawns a {@link RepeatingTimer} that calls
	 * {@link IncendiaryAction#fireOnce(Player)} every tick-rate ticks while RMB is held, plus a watchdog that cancels
	 * the loop a few ticks after the player releases RMB. The release-detection mechanism reuses
	 * {@link #continuousFire} — the {@code shooting} flag is set on every {@link PlayerInteractEvent} and cleared by
	 * the watchdog.
	 */
	private void handleIncendiaryAuto(IncendiaryWeapon weapon, IncendiaryAction action, Player player) {
		UUID weaponUuid = weapon.getUuid();

		EmptyMagSoundGate.refresh(weaponUuid);

		AtomicReference<WeaponData> existing = continuousFire.get(weaponUuid);
		if (existing != null) {
			// already running — just refresh the held flag so the watchdog doesn't kill it
			existing.get().shooting = true;
			return;
		}

		WeaponData freshWeaponData = new WeaponData();
		freshWeaponData.shooting = true;
		AtomicReference<WeaponData> ref = new AtomicReference<>(freshWeaponData);
		continuousFire.put(weaponUuid, ref);

		int tickRate = Math.max(1, weapon.getIncendiaryData().getTickRate());
		RepeatingTimer sprayLoop = new RepeatingTimer(plugin, tickRate, time -> {
			if (!action.fireOnce(player)) {
				time.stop();
				activeTasks.remove(weaponUuid);
				continuousFire.remove(weaponUuid);
			}
		});
		sprayLoop.start(false);
		activeTasks.put(weaponUuid, sprayLoop);

		// release-detection watchdog — mirrors the AUTO gun pattern
		new RepeatingTimer(plugin, tickRate + 3L, time -> {
			AtomicReference<WeaponData> stillShooting = continuousFire.get(weaponUuid);
			if (stillShooting == null) {
				time.stop();
				return;
			}
			if (!stillShooting.get().shooting) {
				time.stop();
				continuousFire.remove(weaponUuid);
				RepeatingTimer running = activeTasks.remove(weaponUuid);
				if (running != null) running.stop();
				return;
			}
			stillShooting.get().shooting = false;
		}).start(true);
	}

	/**
	 * SINGLE/BURST press gate. Returns {@code true} if the current RMB event should be dropped — either because the
	 * held-trigger watchdog is still tracking RMB-held state from a prior shot (in which case the held flag is
	 * refreshed so the watchdog knows RMB is still down), or because the per-shot cooldown has not expired yet. Returns
	 * {@code false} if the press is genuine and the caller should fire — in which case the caller MUST follow up with
	 * {@link #engagePressHoldWatchdog(UUID, long)} so the next held-RMB event is correctly suppressed.
	 */
	private boolean isPressGated(UUID weaponUuid) {
		AtomicReference<WeaponData> held = pressHoldState.get(weaponUuid);
		if (held != null) {
			// already in hold state — refresh the flag so the watchdog knows RMB is still down
			held.get().shooting = true;
			EmptyMagSoundGate.refresh(weaponUuid);
			return true;
		}

		// cooldown gate (rapid release-and-press faster than the weapon's natural fire rate)
		Long lockedUntil = pressLockUntilTick.get(weaponUuid);
		return lockedUntil != null && System.currentTimeMillis() < lockedUntil;
	}

	/**
	 * Records the per-shot cooldown deadline and starts the held-trigger watchdog for the given weapon. Must be called
	 * immediately after a SINGLE/BURST shot fires. The watchdog reads the {@link WeaponData#shooting} flag every
	 * {@link #MIN_PRESS_LOCK_TICKS} ticks and clears the weapon's entry once it observes one full cycle without a
	 * refresh — indicating the player has released RMB and the trigger should be re-armed for the next press. Until the
	 * entry is cleared, {@link #isPressGated(UUID)} will continue to drop incoming events for this weapon.
	 */
	private void engagePressHoldWatchdog(UUID weaponUuid, long lockTicks) {
		long lockMillis = lockTicks * MILLIS_PER_TICK;
		pressLockUntilTick.put(weaponUuid, System.currentTimeMillis() + lockMillis);

		WeaponData freshWeaponData = new WeaponData();
		freshWeaponData.shooting = true;
		AtomicReference<WeaponData> ref = new AtomicReference<>(freshWeaponData);
		pressHoldState.put(weaponUuid, ref);

		// release-detection watchdog. Pure flag-flipping + map mutation, so safe to run async.
		new RepeatingTimer(plugin, MIN_PRESS_LOCK_TICKS, time -> {
			AtomicReference<WeaponData> stillHeld = pressHoldState.get(weaponUuid);
			if (stillHeld == null) {
				// already cleared (e.g., by weapon swap)
				time.stop();
				return;
			}
			if (!stillHeld.get().shooting) {
				// no PlayerInteractEvent refreshed the flag in the last cycle — RMB has been released. Re-arm the
				// trigger so the next press fires a fresh shot.
				time.stop();
				pressHoldState.remove(weaponUuid);
				return;
			}
			stillHeld.get().shooting = false;
		}).start(true);
	}

	/**
	 * Press-locked SINGLE/BURST trigger.
	 *
	 * <p>Spigot fires {@link PlayerInteractEvent} repeatedly while the client holds RMB on a weapon item — there is no
	 * first-class "edge press" signal. To make SINGLE/BURST behave as one-shot-per-press despite this, we run a
	 * release-detection watchdog ({@link #engagePressHoldWatchdog(UUID, long)}) that drops every event arriving while a
	 * previous trigger pull is still "held". The watchdog only clears its entry once it observes a quiet tick window
	 * (the held-RMB packet stream has stopped), at which point the trigger is re-armed for the next genuine press.
	 *
	 * <p>The cooldown gate ({@link #pressLockUntilTick}) is preserved as an orthogonal rate limiter that prevents
	 * firing faster than the weapon's natural fire rate even when the player release-and-re-presses RMB rapidly.
	 */
	private void shootOtherModes(GunWeapon weapon, Player player) {
		UUID weaponUuid = weapon.getUuid();

		if (isPressGated(weaponUuid)) return;

		var projectileData = weapon.getProjectileData();
		long lockTicks = Math.max((long) projectileData.getPerShot() * projectileData.getCooldown(),
		                          MIN_PRESS_LOCK_TICKS);

		engagePressHoldWatchdog(weaponUuid, lockTicks);

		// fire one shot (SINGLE) or one burst sequence (BURST). The inner SequenceTimer in shoot()
		// already spaces individual burst rounds by projectileCooldown.
		shoot(player, weapon);

		// recoil is reset when the lock window expires so the next press starts on a fresh pattern
		new CountdownTimer(plugin, 0L, 0L, lockTicks, null, null,
		                   timer -> weapon.getRecoil().resetRecoilPattern()).start(false);
	}

	private void shootFullAuto(GunWeapon weapon, Player player, ItemStack item) {
		UUID weaponUuid = weapon.getUuid();
		if (!autoTasks.containsKey(weaponUuid)) {
			var autoTask = new FullAutoTask(plugin, weaponService, weapon, recoilCompatibility, raytracer, player, item,
			                                () -> {
												autoTasks.remove(weaponUuid);
												continuousFire.remove(weaponUuid);
											});

			autoTasks.put(weaponUuid, autoTask);

			WeaponData freshWeaponData = new WeaponData();
			freshWeaponData.shooting = true;
			AtomicReference<WeaponData> weaponDataAtomicReference = new AtomicReference<>(freshWeaponData);

			continuousFire.put(weaponUuid, weaponDataAtomicReference);

			autoTask.start(false);

			// watchdog timer for AUTO mode
			long watchdog = weapon.getProjectileData().getCooldown() + 2L;
			new RepeatingTimer(plugin, watchdog, time -> {
				AtomicReference<WeaponData> stillShooting = continuousFire.get(weaponUuid);

				if (stillShooting == null) {
					time.stop();
					weapon.getRecoil().resetRecoilPattern();
					return;
				}

				if (!stillShooting.get().shooting) {
					FullAutoTask task = autoTasks.get(weaponUuid);

					if (task != null) {
						task.cancel();
					}

					time.stop();
					continuousFire.remove(weaponUuid);
					weapon.getRecoil().resetRecoilPattern();
					return;
				}

				stillShooting.get().shooting = false;
			}).start(true);
		} else {
			AtomicReference<WeaponData> weaponData = continuousFire.get(weaponUuid);

			if (weaponData != null) {
				weaponData.get().shooting = true;
				EmptyMagSoundGate.refresh(weaponUuid);
			}
		}
	}

	private void shoot(Player player, GunWeapon weapon) {
		// have only multiple shots for when the weapon is burst
		int numberOfShots = 1;

		if (weapon.getCurrentSelectiveFire() == SelectiveFire.BURST) numberOfShots = weapon.getProjectileData()
		                                                                                   .getPerShot();

		SequenceTimer sequenceTimer = new SequenceTimer(plugin, 1L, 1L);

		for (int i = 0; i < numberOfShots; ++i) {
			// logically, the first shot should be instant
			int cooldown = i == 0 ? 0 : weapon.getProjectileData().getCooldown();

			sequenceTimer.addIntervalTaskPair(cooldown, time -> shootInterval(player, weapon));
		}

		sequenceTimer.start(false);
	}

	private void shootInterval(Player player, GunWeapon weapon) {
		GunAction gunAction = new GunAction(plugin, weaponService, weapon, recoilCompatibility, raytracer);

		// shoot the weapon
		gunAction.weaponShoot(player);

		// weapon consumption
		if (weapon.getWeaponConsumedOnShot() > 0 &&
		    weapon.getCurrentMagCapacity() == weapon.getWeaponConsumedOnShot()) {
			weapon.removeWeapon(player, player.getInventory().getHeldItemSlot());
		}

		int consumeOnTime = weapon.getDurabilityData().getConsumeOnTime();
		if (consumeOnTime <= -1) return;

		CountdownTimer timer = new CountdownTimer(plugin, 0L, 0L, consumeOnTime, null, null,
		                                          time -> weapon.removeWeapon(player,
		                                                                      player.getInventory().getHeldItemSlot()));

		timer.start(false);
	}

	/**
	 * Held-RMB shared state. The {@link #shooting} flag is set to {@code true} on every RMB {@link PlayerInteractEvent}
	 * while a fire-task is active and cleared by a watchdog after one idle cycle; this is how the listener detects RMB
	 * release without a first-class "edge release" signal from Spigot.
	 *
	 * <ul>
	 *   <li>AUTO uses this via {@link #continuousFire} to know when to stop the {@link FullAutoTask}.
	 *   <li>SINGLE/BURST uses this via {@link #pressHoldState} to know when the trigger should be re-armed for the
	 *       next press (the cooldown gate {@link #pressLockUntilTick} alone is not sufficient — once it lapses
	 *       mid-hold, the next held-RMB event would otherwise fire a second shot).
	 *   <li>Biological charge-then-release uses this via {@link #continuousFire} to know when to fire the charged
	 *       shot.
	 * </ul>
	 */
	private static class WeaponData {

		private boolean shooting;

	}

}
