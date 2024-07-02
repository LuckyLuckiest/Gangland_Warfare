package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.file.configuration.SoundConfiguration;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class WeaponInteract implements Listener {

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	private final Map<UUID, Pair<AtomicBoolean, AtomicBoolean>> continuousFire;

	public WeaponInteract(Gangland gangland) {
		this.gangland       = gangland;
		this.weaponManager  = gangland.getInitializer().getWeaponManager();
		this.continuousFire = new ConcurrentHashMap<>();
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

		// check if the pair exists
		Pair<AtomicBoolean, AtomicBoolean> pair = continuousFire.get(weapon.getUuid());
		// create a new instance
		Pair<AtomicBoolean, AtomicBoolean> newPair = getContinuityAndCooldownPair(pair, weapon);

		if (pair == null) {
			// create a new instance and insert it in
			continuousFire.put(weapon.getUuid(), newPair);

			// run each process each second
			RepeatingTimer continuousTimer = new RepeatingTimer(gangland, 20L, time -> {
				// get the necessary information
				Pair<AtomicBoolean, AtomicBoolean> retrievedPair = continuousFire.get(weapon.getUuid());

				// the first boolean is for continuous firing
				boolean continueFiring = retrievedPair.second().get();
				// the second boolean is for waiting on a cooldown
				boolean waitCooldown = retrievedPair.first().get();

				if (!continueFiring) {
					time.stop();
					return;
				}

				// otherwise wait for cooldown
				if (waitCooldown) return;

			});

			continuousTimer.start(false);
		} else {
			// modify the pair value
			continuousFire.replace(weapon.getUuid(), newPair);
		}

//		CountdownTimer continuousTimer = new CountdownTimer(gangland, 1, // each second should check if it was shooting
//															null, // no need to do anything before starting the timer
//															time -> {
//																// check if the weapon should be shooting or not
//																if (continuousFire.containsKey(weapon.getUuid()))
//																	// shoot the weapon
//																	shoot(player, weapon);
//															}, time ->
//																	// remove the timer from the continuous timer
//																	continuousFire.remove(weapon.getUuid()));
//
//		continuousTimer.start(false);
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
	}

	@NotNull
	private Pair<AtomicBoolean, AtomicBoolean> getContinuityAndCooldownPair(Pair<AtomicBoolean, AtomicBoolean> pair,
																			Weapon weapon) {
		AtomicBoolean continuous;
		AtomicBoolean cooldown;

		// if the weapon is in continuous fire, then get the stored data
		if (pair != null) {
			continuous = pair.first();
			cooldown   = pair.second();
		}
		// else create new data
		else {
			continuous = new AtomicBoolean();
			cooldown   = new AtomicBoolean();
		}

		// check the selective fire
		switch (weapon.getCurrentSelectiveFire()) {
			case AUTO -> {
				// should be continuous
				continuous.set(true);
				cooldown.set(false);
			}
			case BURST -> {
				// should be continuous and wait for the cooldown
				continuous.set(true);
				cooldown.set(true);
			}
			case SINGLE -> {
				// should be only once
				continuous.set(false);
				cooldown.set(false);
			}
		}

		return new Pair<>(continuous, cooldown);
	}

	private void shoot(Player player, Weapon weapon) {
		SequenceTimer sequenceTimer = new SequenceTimer(gangland, 0, 1);

		for (int i = 0; i < weapon.getProjectilePerShot(); ++i)
			 sequenceTimer.addIntervalTaskPair(weapon.getProjectileCooldown(), time -> shootInterval(player, weapon));

		sequenceTimer.start(false);
	}

	private void shootInterval(Player player, Weapon weapon) {
		// consume bullet
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
		float recoil = (float) weapon.getRecoilAmount();

		if (!player.isSneaking()) recoil(player, recoil, recoil);
		else {
			float newValue = recoil / 2;

			if (weapon.isScoped()) recoil(player, newValue, newValue);
			else recoil(player, newValue / 2, newValue / 2);
		}

		// apply push
		if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
		else {
			if (weapon.isScoped()) push(player, weapon.getPushPowerUp() / 2, weapon.getPushVelocity() / 2);
			else push(player, 0, 0);
		}

		// shooting sound
		SoundConfiguration.playSounds(player, weapon.getShotCustomSound(), weapon.getShotDefaultSound());

		// weapon consumption
		if (weapon.getWeaponConsumedOnShot() > 0 &&
			weapon.getCurrentMagCapacity() == weapon.getWeaponConsumedOnShot()) {
			weapon.removeWeapon(player, player.getInventory().getHeldItemSlot());
		}

		if (weapon.getWeaponConsumeOnTime() <= -1) return;

		CountdownTimer timer = new CountdownTimer(gangland, weapon.getWeaponConsumeOnTime(), null, null,
												  time -> weapon.removeWeapon(player,
																			  player.getInventory().getHeldItemSlot()));

		timer.start(false);
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

}
