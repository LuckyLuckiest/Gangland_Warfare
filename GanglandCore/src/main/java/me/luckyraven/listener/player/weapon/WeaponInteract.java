package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.timer.CountdownTimer;
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

public class WeaponInteract implements Listener {

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	public WeaponInteract(Gangland gangland) {
		this.gangland      = gangland;
		this.weaponManager = gangland.getInitializer().getWeaponManager();
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

		// check the selective fire
//		switch (weapon.getCurrentSelectiveFire()) {
//			case AUTO -> {
//				// should be continuous
//				return;
//			}
//			case SINGLE, BURST -> {
//				// should wait for the cooldown before shooting
//				return;
//			}
//		}

		SequenceTimer sequenceTimer = new SequenceTimer(gangland, 0, 1);

		for (int i = 0; i < weapon.getProjectilePerShot(); ++i) {
			sequenceTimer.addIntervalTaskPair(weapon.getProjectileCooldown(), time -> {
				// consume bullet
				boolean consumed = weapon.consumeShot();

				// no shot fired
				if (!consumed) {
					// sound
					SoundConfiguration.playSounds(player, weapon.getEmptyMagCustomSound(),
												  weapon.getEmptyMagDefaultSound());

					return;
				}

				WeaponProjectile<?> weaponProjectile = weapon.getProjectileType()
															 .createInstance(gangland, player, weapon);
				WeaponShootEvent weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);
				gangland.getServer().getPluginManager().callEvent(weaponShootEvent);

				// launch the projectile
				if (weaponShootEvent.isCancelled()) return;

				weaponProjectile.launchProjectile();

				// update data
				ItemBuilder heldWeapon = weaponManager.getHeldWeaponItem(player);

				if (heldWeapon != null) {
					weapon.updateWeaponData(heldWeapon);
					weapon.updateWeapon(player, heldWeapon, player.getInventory().getHeldItemSlot());
				}

				float recoil = (float) weapon.getRecoilAmount();

				if (!player.isSneaking()) recoil(player, recoil, recoil);
				else {
					float newValue = recoil / 2;

					if (weapon.isScoped()) recoil(player, newValue, newValue);
					else recoil(player, newValue / 2, newValue / 2);
				}

				if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
				else {
					if (weapon.isScoped()) push(player, weapon.getPushPowerUp() / 2, weapon.getPushVelocity() / 2);
					else push(player, 0, 0);
				}

				SoundConfiguration.playSounds(player, weapon.getShotCustomSound(), weapon.getShotDefaultSound());

				// weapon consumption
				if (weapon.getWeaponConsumedOnShot() > 0 &&
					weapon.getCurrentMagCapacity() == weapon.getWeaponConsumedOnShot()) {
					weapon.removeWeapon(player, player.getInventory().getHeldItemSlot());
				}

				if (weapon.getWeaponConsumeOnTime() > -1) {
					CountdownTimer timer = new CountdownTimer(gangland, weapon.getWeaponConsumeOnTime(), null, null,
															  time1 -> {
																  weapon.removeWeapon(player, player.getInventory()
																									.getHeldItemSlot());
															  });

					timer.start(false);
				}
			});
		}

		sequenceTimer.start(false);
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
