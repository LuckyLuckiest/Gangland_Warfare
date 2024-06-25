package me.luckyraven.listener.player.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

public class WeaponInteract implements Listener {

	private final Gangland gangland;

	public WeaponInteract(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		// check if it was a weapon
		ItemStack item = event.getItem();

		if (item == null) return;
		if (!Weapon.isWeapon(item)) return;

		String weaponName = Weapon.getHeldWeaponName(item);
		if (weaponName == null) return;

		// get the weapon information
		Player player = event.getPlayer();
		UUID   uuid   = Weapon.getWeaponUUID(item);

		// Need to change how the weapons are got, basically there can be a repeated pattern of similar weapons sharing
		// similar traits but are different fundamentally.
		// A solution for this is to have all the weapons loaded and stored in a map.
		// The weapons acquired by the user are created in that instance as a new weapon, which takes all the traits
		// stored.
		Weapon weapon = gangland.getInitializer().getWeaponManager().getWeapon(uuid, weaponName);
		if (weapon == null) return;

		// left-click scopes
		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		if (leftClick) {

			return;
		}

		// pre-shot event


		// right-click shoots
		boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR ||
							 event.getAction() == Action.RIGHT_CLICK_BLOCK;
		if (rightClick) {
			// consume bullet
			boolean consumed = weapon.consumeShot();

			// no shot fired
			if (!consumed) {
				// sound
				boolean sound = playSound(player, weapon.getCustomMagSound());
				if (!sound) playSound(player, weapon.getDefaultMagSound());

				return;
			}

			WeaponProjectile<?> weaponProjectile = weapon.getProjectileType().createInstance(gangland, player, weapon);
			WeaponShootEvent    weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);

			// launch the projectile
			if (weaponShootEvent.isCancelled()) return;

			weaponProjectile.launchProjectile();

			// update data
			ItemBuilder heldWeapon = weapon.getHeldWeapon(player);

			if (heldWeapon != null) {
				weapon.updateWeaponData(heldWeapon);
				weapon.updateWeapon(player, heldWeapon, player.getInventory().getHeldItemSlot());
			}

			float recoil = (float) weapon.getRecoilAmount();

			if (!player.isSneaking()) recoil(player, recoil, recoil);
			else recoil(player, recoil / 4, recoil / 4);

			if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
			else push(player, 0, 0);

			// TODO fix the sound
			boolean shotSound = playSound(player, weapon.getCustomShotSound());
			if (!shotSound) playSound(player, weapon.getDefaultShotSound());

			gangland.getServer().getPluginManager().callEvent(weaponShootEvent);
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

	private boolean playSound(Player player, SoundConfiguration sound) {
		if (sound == null) return false;

		return sound.playSound(player);
	}

}
