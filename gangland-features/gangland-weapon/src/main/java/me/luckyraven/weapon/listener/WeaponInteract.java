package me.luckyraven.weapon.listener;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.Pair;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.dto.ScopeData;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ListenerHandler
@AutowireTarget({WeaponService.class, RecoilCompatibility.class})
public class WeaponInteract implements Listener {

	private final JavaPlugin          plugin;
	private final WeaponService       weaponService;
	private final RecoilCompatibility recoilCompatibility;

	private final Map<UUID, AtomicReference<WeaponData>> continuousFire;
	private final Map<UUID, Boolean>                     singleShotLock;
	private final Map<UUID, FullAutoTask>                autoTasks;
	private final Map<UUID, RepeatingTimer>              activeTasks;
	private final Map<UUID, Long>                        meleeCooldowns;

	public WeaponInteract(JavaPlugin plugin, WeaponService weaponService, RecoilCompatibility recoilCompatibility) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.recoilCompatibility = recoilCompatibility;
		this.continuousFire      = new ConcurrentHashMap<>();
		this.singleShotLock      = new ConcurrentHashMap<>();
		this.autoTasks           = new ConcurrentHashMap<>();
		this.activeTasks         = new ConcurrentHashMap<>();
		this.meleeCooldowns      = new ConcurrentHashMap<>();
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player = event.getPlayer();
		ItemStack item   = event.getItem();
		Weapon    weapon = weaponService.validateAndGetWeapon(player, item);

		if (weapon == null) return;

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

		// allow damage that was applied programmatically by MeleeAction or ThrowableAction itself
		UUID targetUuid = event.getEntity().getUniqueId();
		if (MeleeAction.pendingDamage.remove(targetUuid)) return;
		if (ThrowableAction.pendingDamage.remove(targetUuid)) return;

		ItemStack item = player.getInventory().getItemInMainHand();
		if (!weaponService.isWeapon(item)) return;

		// cancel the default Minecraft attack damage for all weapon types
		event.setCancelled(true);

		Weapon weapon = weaponService.validateAndGetWeapon(player, item);
		if (weapon == null) return;

		if (weapon instanceof MeleeWeapon meleeWeapon) {
			boolean hit = new MeleeAction(meleeWeapon, recoilCompatibility, meleeCooldowns).activate(player);
			if (hit) meleeWeapon.applyOnHitDurability(player, player.getInventory().getHeldItemSlot());
		}
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

		if (weapon instanceof GunWeapon gunWeapon) {
			// remove single shot lock for the weapon
			singleShotLock.remove(weaponUuid);

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
	}

	private void handleNonGunInteract(PlayerInteractEvent event, Player player, Weapon weapon, boolean leftClick,
	                                  boolean rightClick) {
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);

		// block all actions while reloading
		if (weapon.isReloading()) return;

		switch (weapon) {
			case ThrowableWeapon throwable -> {
				if (rightClick) new ThrowableAction(plugin, throwable, recoilCompatibility).activate(player);
			}
			case MeleeWeapon melee -> {
				if (leftClick) {
					boolean hit = new MeleeAction(melee, recoilCompatibility, meleeCooldowns).activate(player);
					if (hit) melee.applyOnHitDurability(player, player.getInventory().getHeldItemSlot());
				}
			}
			case IncendiaryWeapon incendiary -> {
				IncendiaryAction action = new IncendiaryAction(plugin, weaponService, incendiary, recoilCompatibility,
				                                               activeTasks);

				if (rightClick) action.start(player);
				else if (leftClick) action.stop();
			}
			case BiologicalWeapon biological -> {
				BiologicalAction action = new BiologicalAction(plugin, biological, recoilCompatibility, activeTasks);

				if (rightClick) action.start(player);
				else if (leftClick) action.fire(player);
			}
			default -> { }
		}
	}

	private void shootOtherModes(GunWeapon weapon, Player player) {
		// check if the pair exists
		UUID weaponUuid = weapon.getUuid();

		AtomicReference<WeaponData> weaponData = continuousFire.get(weaponUuid);

		// prevent holding the firing for multiple times
		if (weapon.getCurrentSelectiveFire() == SelectiveFire.SINGLE) {
			if (singleShotLock.getOrDefault(weaponUuid, false)) {
				return;
			}

			singleShotLock.put(weaponUuid, true);
		}

		if (weaponData == null) {
			// create a new instance
			WeaponData finalWeaponData = getWeaponData(null, weapon);

			// create a new instance and insert it in
			continuousFire.put(weaponUuid, new AtomicReference<>(finalWeaponData));

			// get the necessary information
			AtomicReference<WeaponData> retrievedWeaponData = continuousFire.get(weaponUuid);

			// run the process each x ticks
			RepeatingTimer shootingTimer = getShootingTimer(retrievedWeaponData, weapon, player);

			// RepeatingTimer skips its first tick (justStarted guard), so without this
			// call the first shot would be delayed by one full cooldown period.
			selectiveFireShooter(weapon, player, shootingTimer, weapon.getCurrentSelectiveFire(), finalWeaponData);

			shootingTimer.start(false);

			// remove the weapon after 3 ticks of not pressing the button
			long watchdog = weapon.getProjectileData().getCooldown() + 3L;
			new RepeatingTimer(plugin, watchdog, time -> {
				// get the necessary information
				AtomicReference<WeaponData> stillShooting = continuousFire.get(weaponUuid);

				if (stillShooting == null) {
					time.stop();
					weapon.getRecoil().resetRecoilPattern();
					return;
				}

				// if the player is still shooting, then don't stop
				if (!stillShooting.get().shooting) {
					time.stop();
					continuousFire.remove(weaponUuid);
					weapon.getRecoil().resetRecoilPattern();
					return;
				}

				stillShooting.get().shooting = false;
			}).start(true);
		} else {
			// modify the value
			weaponData.get().shooting = true;
		}
	}

	private void shootFullAuto(GunWeapon weapon, Player player, ItemStack item) {
		UUID weaponUuid = weapon.getUuid();
		if (!autoTasks.containsKey(weaponUuid)) {
			var autoTask = new FullAutoTask(plugin, weaponService, weapon, recoilCompatibility, player, item, () -> {
				autoTasks.remove(weaponUuid);
				continuousFire.remove(weaponUuid);
			});

			autoTasks.put(weaponUuid, autoTask);

			Pair<Boolean, Boolean> continuityAndCooldown = getContinuityAndCooldownPair(
					weapon.getCurrentSelectiveFire());
			AtomicReference<WeaponData> weaponDataAtomicReference = new AtomicReference<>(
					new WeaponData(continuityAndCooldown.first(), continuityAndCooldown.second()));

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
			}
		}
	}

	@NotNull
	private RepeatingTimer getShootingTimer(AtomicReference<WeaponData> retrievedWeaponData, GunWeapon weapon,
	                                        Player player) {
		return new RepeatingTimer(plugin, weapon.getProjectileData().getCooldown(), time -> {
			if (retrievedWeaponData == null) {
				time.stop();
				return;
			}

			// shot already and not continuous
			WeaponData data = retrievedWeaponData.get();

			if (!data.shooting) {
				UUID weaponUuid = weapon.getUuid();
				continuousFire.remove(weaponUuid);
				time.stop();
				return;
			}

			// handle the weapon according to the selective fire
			selectiveFireShooter(weapon, player, time, weapon.getCurrentSelectiveFire(), data);
		});
	}

	private void selectiveFireShooter(GunWeapon weapon, Player player, RepeatingTimer time, SelectiveFire selectiveFire,
	                                  WeaponData data) {
		var projectileData = weapon.getProjectileData();
		switch (selectiveFire) {
			case AUTO -> { }
			case BURST -> {
				if (data.cooldown) return;

				data.cooldown = true;
				shoot(player, weapon);

				// calculate total burst time
				long burstDuration = (long) projectileData.getPerShot() * projectileData.getCooldown();

				// reset after burst delay
				new CountdownTimer(plugin, 0L, 0L, burstDuration, null, null, timer -> {
					data.cooldown = false;
				}).start(false);
			}
			case SINGLE -> {
				if (data.cooldown) return;

				data.cooldown = true;
				shoot(player, weapon);
				data.shooting = false;

				UUID weaponUuid     = weapon.getUuid();
				long singleDuration = (long) projectileData.getPerShot() * projectileData.getCooldown();

				new CountdownTimer(plugin, 0L, 0L, singleDuration, null, null, timer -> {
					data.cooldown = false;
					singleShotLock.remove(weaponUuid);
					weapon.getRecoil().resetRecoilPattern();
				}).start(false);

				continuousFire.remove(weaponUuid);
				time.stop();
			}
		}
	}

	@NotNull
	private WeaponData getWeaponData(@Nullable WeaponData weaponData, @NotNull GunWeapon weapon) {
		WeaponData finalWeaponData;

		// if the weapon is in continuous fire, then get the stored data
		if (weaponData != null) {
			finalWeaponData = new WeaponData(weaponData.continuous, weaponData.cooldown);
		}
		// else create new data
		else {
			// check the selective fire
			Pair<Boolean, Boolean> continuityAndCooldown = getContinuityAndCooldownPair(
					weapon.getCurrentSelectiveFire());
			finalWeaponData = new WeaponData(continuityAndCooldown.first(), continuityAndCooldown.second());
		}

		finalWeaponData.shooting = true;

		return finalWeaponData;
	}

	@NotNull
	private Pair<Boolean, Boolean> getContinuityAndCooldownPair(@NotNull SelectiveFire selectiveFire) {
		// let the timer repeat, but the behavior is handled per the mode
		return new Pair<>(true, false);
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

		GunAction gunAction = new GunAction(plugin, weaponService, weapon, recoilCompatibility);

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

	private static class WeaponData {

		private final boolean continuous;
		private       boolean shooting, cooldown;

		public WeaponData(boolean continuous, boolean cooldown) {
			this.continuous = continuous;
			this.cooldown   = cooldown;
		}

	}

}
