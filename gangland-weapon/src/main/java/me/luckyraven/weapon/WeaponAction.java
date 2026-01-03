package me.luckyraven.weapon;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.dto.RecoilData;
import me.luckyraven.weapon.dto.SoundData;
import me.luckyraven.weapon.events.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class WeaponAction {

	private final JavaPlugin          plugin;
	private final WeaponService       weaponService;
	private final Weapon              weapon;
	private final RecoilCompatibility recoilCompatibility;

	public WeaponAction(JavaPlugin plugin, WeaponService weaponService, Weapon weapon,
						RecoilCompatibility recoilCompatibility) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
	}

	public void weaponShoot(Player shooter) {
		// update data
		ItemBuilder heldWeapon = weaponService.getHeldWeaponItem(shooter);

		if (heldWeapon == null) {
			return;
		}

		// check the durability of the weapon
		SoundData soundData = weapon.getSoundData();
		if (weapon.isBroken()) {
			SoundConfiguration.playSounds(shooter, soundData.getEmptyMagCustom(), soundData.getEmptyMagDefault());

			ChatUtil.sendActionBar(shooter, "&cBroken");
			return;
		}

		// consume a bullet
		boolean consumed = weapon.consumeShot();

		// no shot fired
		if (!consumed) {
			// empty magazine sound
			SoundConfiguration.playSounds(shooter, soundData.getEmptyMagCustom(), soundData.getEmptyMagDefault());
			return;
		}

		WeaponProjectile<?> weaponProjectile = weapon.getProjectileData()
													 .getType()
													 .createInstance(plugin, shooter, weapon);
		WeaponShootEvent weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);
		Bukkit.getPluginManager().callEvent(weaponShootEvent);

		// launch the projectile
		if (weaponShootEvent.isCancelled()) {
			// substitute for the consumed shot
			weapon.addAmmunition(1);
			return;
		}

		weaponProjectile.launchProjectile();

		weapon.updateWeaponData(heldWeapon);

		// change durability of the weapon
		short durabilityOnShot = weapon.getDurabilityData().getOnShot();
		if (durabilityOnShot > (short) 0) {
			weapon.decreaseDurability(heldWeapon, durabilityOnShot);
		}

		weapon.updateWeapon(shooter, heldWeapon, shooter.getInventory().getHeldItemSlot());

		// apply recoil
		weapon.getRecoil().applyRecoil(recoilCompatibility, shooter);

		// apply push
		applyPush(shooter, weapon);

		// shooting sound
		SoundConfiguration.playSounds(shooter, weapon.getSoundData().getShotCustom(),
									  weapon.getSoundData().getShotDefault());
	}

	private void applyPush(Player player, Weapon weapon) {
		// Never apply push if player is not on solid ground
		if (!isPlayerGrounded(player)) {
			return;
		}

		RecoilData recoilData = weapon.getRecoilData();
		if (!player.isSneaking()) {
			push(player, recoilData.getPushPowerUp(), recoilData.getPushVelocity());
			return;
		}
		if (weapon.getScopeData().isScoped()) {
			push(player, recoilData.getPushPowerUp() / 2, recoilData.getPushVelocity() / 2);
		}
		// When sneaking and not scoped, no push is applied (implicitly returns)
	}

	private void push(Player player, double powerUp, double push) {
		// Safety check - clamp values to reasonable limits
		powerUp = Math.max(-0.5, Math.min(0.5, powerUp));
		push    = Math.max(-0.5, Math.min(0.5, push));

		if (push > 0) push *= -1;

		// Don't apply if values are effectively zero
		if (Math.abs(powerUp) < 0.001 && Math.abs(push) < 0.001) {
			return;
		}

		Location location  = player.getLocation();
		Vector   direction = location.getDirection().multiply(push);
		Vector   upward    = new Vector(0, powerUp, 0);
		Vector   velocity  = direction.add(upward);

		// Clamp final velocity to prevent "moved too quickly" warnings
		double maxSpeed = 1.0; // Reasonable max speed
		if (velocity.length() > maxSpeed) {
			velocity.normalize().multiply(maxSpeed);
		}

		player.setVelocity(velocity);
	}

	/**
	 * Checks if the player is firmly on the ground. Returns false if jumping, flying, in creative flight, swimming,
	 * etc.
	 */
	private boolean isPlayerGrounded(Player player) {
		// Check if player is flying (creative/spectator or elytra)
		if (player.isFlying() || player.isGliding()) {
			return false;
		}

		// Check if player is swimming or in water
		if (player.isSwimming() || player.isInWater()) {
			return false;
		}

		// Check if player is climbing (ladders, vines)
		if (player.isClimbing()) {
			return false;
		}

		// Check the block below - must be solid ground
		Location playerLoc  = player.getLocation();
		Block    blockBelow = playerLoc.subtract(0, 0.1, 0).getBlock();

		if (!blockBelow.getType().isSolid()) {
			return false;
		}

		// Check player's Y velocity - if moving up or down significantly, not grounded
		double yVelocity = player.getVelocity().getY();
		return !(Math.abs(yVelocity) > 0.1);
	}

}
