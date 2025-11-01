package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.FullAutoTask;
import me.luckyraven.feature.weapon.SelectiveFire;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.Pair;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
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
import org.bukkit.util.Vector;
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
			if (!autoTasks.containsKey(weapon.getUuid())) {
				FullAutoTask autoTask = new FullAutoTask(gangland, weapon, player, item, () -> {
					autoTasks.remove(weapon.getUuid());
					continuousFire.remove(weapon.getUuid());
				});

				autoTasks.put(weapon.getUuid(), autoTask);

				Pair<Boolean, Boolean> continuityAndCooldown = getContinuityAndCooldownPair(selectiveFire);
				AtomicReference<WeaponData> weaponDataAtomicReference = new AtomicReference<>(
						new WeaponData(continuityAndCooldown.first(), continuityAndCooldown.second()));

				continuousFire.put(weapon.getUuid(), weaponDataAtomicReference);

				autoTask.start(false);

				// watchdog timer for AUTO mode
				new RepeatingTimer(gangland, 3L, time -> {
					AtomicReference<WeaponData> stillShooting = continuousFire.get(weapon.getUuid());

					if (stillShooting == null) {
						time.stop();
						return;
					}

					if (!stillShooting.get().shooting) {
						FullAutoTask task = autoTasks.get(weapon.getUuid());

						if (task != null) {
							task.cancel();
						}

						time.stop();
						continuousFire.remove(weapon.getUuid());
						return;
					}

					stillShooting.get().shooting = false;
				}).start(true);
			} else {
				AtomicReference<WeaponData> weaponData = continuousFire.get(weapon.getUuid());

				if (weaponData != null) {
					weaponData.get().shooting = true;
				}
			}

			return;
		}

		// handle the BURST and SINGLE modes
		// check if the pair exists
		AtomicReference<WeaponData> weaponData = continuousFire.get(weapon.getUuid());

		// prevent holding the firing for multiple times
		if (weapon.getCurrentSelectiveFire() == SelectiveFire.SINGLE) {
			UUID playerId = player.getUniqueId();
			if (singleShotLock.getOrDefault(playerId, false)) {
				return;
			}

			singleShotLock.put(playerId, true);
		}

		if (weaponData == null) {
			// create a new instance
			WeaponData finalWeaponData = getWeaponData(null, weapon);

			// create a new instance and insert it in
			continuousFire.put(weapon.getUuid(), new AtomicReference<>(finalWeaponData));

			// get the necessary information
			AtomicReference<WeaponData> retrievedWeaponData = continuousFire.get(weapon.getUuid());

			// run the process each x ticks
			RepeatingTimer shootingTimer = getShootingTimer(retrievedWeaponData, weapon, player);

			shootingTimer.start(false);

			// remove the weapon after 1 second of not pressing the button
			new RepeatingTimer(gangland, 2L, time -> {
				// get the necessary information
				AtomicReference<WeaponData> stillShooting = continuousFire.get(weapon.getUuid());

				if (stillShooting == null) {
					time.stop();
					return;
				}

				// if the player is still shooting, then don't stop
				if (!stillShooting.get().shooting) {
					time.stop();
					continuousFire.remove(weapon.getUuid());
					return;
				}

				stillShooting.get().shooting = false;
			}).start(true);
		} else {
			// modify the value
			weaponData.get().shooting = true;
		}
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

		// cancel any active auto fire
		FullAutoTask autoTask = autoTasks.get(weapon.getUuid());

		if (autoTask != null) {
			autoTask.stop();
		}
	}

	@NotNull
	private RepeatingTimer getShootingTimer(AtomicReference<WeaponData> retrievedWeaponData, Weapon weapon,
											Player player) {
		long interval = weapon.getCurrentSelectiveFire() == SelectiveFire.AUTO ? weapon.getProjectileCooldown() : 1L;

		return new RepeatingTimer(gangland, interval, time -> {
			if (retrievedWeaponData == null) {
				time.stop();
				return;
			}

			// shot already and not continuous
			WeaponData data = retrievedWeaponData.get();

			if (!data.shooting) {
				continuousFire.remove(weapon.getUuid());
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
				shoot(player, weapon);
				data.shooting = false;

				UUID playerId = player.getUniqueId();
				new CountdownTimer(gangland, 0L, 0L, 5L, null, null, timer -> singleShotLock.remove(playerId)).start(
						false);

				continuousFire.remove(weapon.getUuid());
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

		SequenceTimer sequenceTimer = new SequenceTimer(gangland, 0, 1);

		for (int i = 0; i < numberOfShots; ++i)
			 sequenceTimer.addIntervalTaskPair(weapon.getProjectileCooldown(), time -> shootInterval(player, weapon));

		sequenceTimer.start(false);
	}

	private void shootInterval(Player player, Weapon weapon) {
		// consume a bullet
		boolean consumed = weapon.consumeShot();

		// no shot fired
		if (!consumed) {
			// empty magazine sound
			SoundConfiguration.playSounds(player, weapon.getEmptyMagCustomSound(), weapon.getEmptyMagDefaultSound());
			return;
		}

		WeaponProjectile<?> weaponProjectile = weapon.getProjectileType().createInstance(gangland, player, weapon);
		WeaponShootEvent    weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);
		gangland.getServer().getPluginManager().callEvent(weaponShootEvent);

		// launch the projectile
		if (weaponShootEvent.isCancelled()) {
			// substitute for the consumed shot
			weapon.addAmmunition(1);
			return;
		}

		weaponProjectile.launchProjectile();

		// update data
		ItemBuilder heldWeapon = weaponManager.getHeldWeaponItem(player);

		if (heldWeapon != null) {
			weapon.updateWeaponData(heldWeapon);
			weapon.updateWeapon(player, heldWeapon, player.getInventory().getHeldItemSlot());
		}

		// apply recoil
		applyRecoil(player, weapon);

		// apply push
		applyPush(player, weapon);

		// shooting sound
		SoundConfiguration.playSounds(player, weapon.getShotCustomSound(), weapon.getShotDefaultSound());

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

	private void applyPush(Player player, Weapon weapon) {
		if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
		else {
			if (weapon.isScoped()) push(player, weapon.getPushPowerUp() / 2, weapon.getPushVelocity() / 2);
			else push(player, 0, 0);
		}
	}

	private void applyRecoil(Player player, Weapon weapon) {
		float recoil = (float) weapon.getRecoilAmount();

		if (!player.isSneaking()) recoil(player, recoil, recoil);
		else {
			float newValue = recoil / 2;

			if (weapon.isScoped()) recoil(player, newValue, newValue);
			else recoil(player, newValue / 2, newValue / 2);
		}
	}

	private void recoil(Player player, float yaw, float pitch) {
		gangland.getInitializer()
				.getCompatibilityWorker()
				.getRecoilCompatibility()
				.modifyCameraRotation(player, yaw, pitch, true);
	}

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) player.setVelocity(
				location.getDirection().multiply(push).add(vector));
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
