package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.*;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.Pair;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ListenerHandler
public class WeaponInteract implements Listener {

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	private final Map<UUID, AtomicReference<WeaponData>> continuousFire;
	private final Map<UUID, Boolean>                     singleShotLock;
	private final Map<UUID, FullAutoTask>                autoTasks;

	public WeaponInteract(Gangland gangland) {
		this.gangland       = gangland;
		this.weaponManager  = gangland.getInitializer().getWeaponManager();
		this.continuousFire = new ConcurrentHashMap<>();
		this.singleShotLock = new ConcurrentHashMap<>();
		this.autoTasks      = new ConcurrentHashMap<>();
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player = event.getPlayer();
		ItemStack item   = event.getItem();
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;

		// no interruption while the weapon is reloading
		if (weapon.isReloading()) {
			event.setCancelled(true);
			return;
		}

		// left-click scopes
		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		if (leftClick) {
			if (!weapon.isScoped()) {
				weapon.scope(player, true);
				SoundConfiguration.playSounds(player, weapon.getScopeCustomSound(), weapon.getScopeDefaultSound());

				return;
			}

			weapon.unScope(player, true);
			SoundConfiguration.playSounds(player, weapon.getScopeCustomSound(), weapon.getScopeDefaultSound());

			return;
		}

		// right-click shoots
		boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR ||
							 event.getAction() == Action.RIGHT_CLICK_BLOCK;
		if (!rightClick) return;

		// cancel block interaction
		event.setUseInteractedBlock(Event.Result.DENY);

		SelectiveFire selectiveFire = weapon.getCurrentSelectiveFire();

		// handle the AUTO mode with full auto task
		if (selectiveFire == SelectiveFire.AUTO) {
			shootFullAuto(weapon, player, item, selectiveFire);
			return;
		}

		// handle the BURST and SINGLE modes
		shootOtherModes(weapon, player);
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!weaponManager.isWeapon(event.getPlayer().getInventory().getItemInMainHand())) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (!weaponManager.isWeapon(event.getPlayer().getInventory().getItemInMainHand())) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onWeaponHeld(PlayerItemHeldEvent event) {
		// check if it was a weapon
		Player    player = event.getPlayer();
		ItemStack item   = player.getInventory().getItem(event.getPreviousSlot());
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;

		weapon.unScope(player, true);

		// reset recoil pattern
		weapon.getRecoil().resetRecoilPattern();

		// remove single shot lock for the weapon
		UUID weaponUuid = weapon.getUuid();
		singleShotLock.remove(weaponUuid);

		// cancel any active auto fire
		FullAutoTask autoTask = autoTasks.get(weaponUuid);

		if (autoTask != null) {
			autoTask.stop();
		}
	}

	private void shootOtherModes(Weapon weapon, Player player) {
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

			shootingTimer.start(false);

			// remove the weapon after 3 ticks of not pressing the button
			long watchdog = weapon.getProjectileCooldown() + 3L;
			new RepeatingTimer(gangland, watchdog, time -> {
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

	private void shootFullAuto(Weapon weapon, Player player, ItemStack item, SelectiveFire selectiveFire) {
		UUID weaponUuid = weapon.getUuid();
		if (!autoTasks.containsKey(weaponUuid)) {
			FullAutoTask autoTask = new FullAutoTask(gangland, weapon, player, item, () -> {
				autoTasks.remove(weaponUuid);
				continuousFire.remove(weaponUuid);
			});

			autoTasks.put(weaponUuid, autoTask);

			Pair<Boolean, Boolean> continuityAndCooldown = getContinuityAndCooldownPair(selectiveFire);
			AtomicReference<WeaponData> weaponDataAtomicReference = new AtomicReference<>(
					new WeaponData(continuityAndCooldown.first(), continuityAndCooldown.second()));

			continuousFire.put(weaponUuid, weaponDataAtomicReference);

			autoTask.start(false);

			// watchdog timer for AUTO mode
			long watchdog = weapon.getProjectileCooldown() + 2L;
			new RepeatingTimer(gangland, watchdog, time -> {
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
	private RepeatingTimer getShootingTimer(AtomicReference<WeaponData> retrievedWeaponData, Weapon weapon,
											Player player) {
		return new RepeatingTimer(gangland, weapon.getProjectileCooldown(), time -> {
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

	private void selectiveFireShooter(Weapon weapon, Player player, RepeatingTimer time, SelectiveFire selectiveFire,
									  WeaponData data) {
		switch (selectiveFire) {
			case AUTO -> { }
			case BURST -> {
				if (data.cooldown) return;

				data.cooldown = true;
				shoot(player, weapon);

				// calculate total burst time
				long burstDuration = (long) weapon.getProjectilePerShot() * weapon.getProjectileCooldown();

				// reset after burst delay
				new CountdownTimer(gangland, 0L, 0L, burstDuration, null, null, timer -> {
					data.cooldown = false;
				}).start(false);
			}
			case SINGLE -> {
				if (data.cooldown) return;

				data.cooldown = true;
				shoot(player, weapon);
				data.shooting = false;

				UUID weaponUuid     = weapon.getUuid();
				long singleDuration = (long) weapon.getProjectilePerShot() * weapon.getProjectileCooldown();

				new CountdownTimer(gangland, 0L, 0L, singleDuration, null, null, timer -> {
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
	private WeaponData getWeaponData(@Nullable WeaponData weaponData, @NotNull Weapon weapon) {
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

	private void shoot(Player player, Weapon weapon) {
		// have only multiple shots for when the weapon is burst
		int numberOfShots = 1;

		if (weapon.getCurrentSelectiveFire() == SelectiveFire.BURST) numberOfShots = weapon.getProjectilePerShot();

		SequenceTimer sequenceTimer = new SequenceTimer(gangland, 1L, 1L);

		for (int i = 0; i < numberOfShots; ++i) {
			// logically, the first shot should be instant
			int cooldown = i == 0 ? 0 : weapon.getProjectileCooldown();

			sequenceTimer.addIntervalTaskPair(cooldown, time -> shootInterval(player, weapon));
		}

		sequenceTimer.start(false);
	}

	private void shootInterval(Player player, Weapon weapon) {
		WeaponAction weaponAction = new WeaponAction(gangland, weapon);

		// shoot the weapon
		weaponAction.weaponShoot(player);

		// weapon consumption
		if (weapon.getWeaponConsumedOnShot() > 0 &&
			weapon.getCurrentMagCapacity() == weapon.getWeaponConsumedOnShot()) {
			weapon.removeWeapon(player, player.getInventory().getHeldItemSlot());
		}

		if (weapon.getWeaponConsumeOnTime() <= -1) return;

		CountdownTimer timer = new CountdownTimer(gangland, 0L, 0L, weapon.getWeaponConsumeOnTime(), null, null,
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
