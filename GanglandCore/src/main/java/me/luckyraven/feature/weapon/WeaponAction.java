package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;
import me.luckyraven.file.configuration.SoundConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class WeaponAction {

	public final Gangland gangland;
	public final Weapon   weapon;

	public WeaponAction(Gangland gangland, Weapon weapon) {
		this.gangland = gangland;
		this.weapon   = weapon;
	}

	public void weaponShoot(Player shooter) {
		// consume a bullet
		boolean consumed = weapon.consumeShot();

		// no shot fired
		if (!consumed) {
			// empty magazine sound
			SoundConfiguration.playSounds(shooter, weapon.getEmptyMagCustomSound(), weapon.getEmptyMagDefaultSound());
			return;
		}

		WeaponProjectile<?> weaponProjectile = weapon.getProjectileType().createInstance(gangland, shooter, weapon);
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
		WeaponManager weaponManager = gangland.getInitializer().getWeaponManager();
		ItemBuilder   heldWeapon    = weaponManager.getHeldWeaponItem(shooter);

		if (heldWeapon != null) {
			weapon.updateWeaponData(heldWeapon);

			// change durability of the weapon
			if (weapon.getDurabilityOnShot() > (short) 0) {
				heldWeapon.decreaseDurability(weapon.getDurabilityOnShot());
			}

			weapon.updateWeapon(shooter, heldWeapon, shooter.getInventory().getHeldItemSlot());
		}

		// apply recoil
		RecoilCompatibility recoilCompatibility = gangland.getInitializer()
														  .getCompatibilityWorker()
														  .getRecoilCompatibility();

		weapon.getRecoil().applyRecoil(recoilCompatibility, shooter);

		// apply push
		applyPush(shooter, weapon);

		// shooting sound
		SoundConfiguration.playSounds(shooter, weapon.getShotCustomSound(), weapon.getShotDefaultSound());
	}

	private void applyPush(Player player, Weapon weapon) {
		if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
		else {
			if (weapon.isScoped()) push(player, weapon.getPushPowerUp() / 2, weapon.getPushVelocity() / 2);
			else push(player, 0, 0);
		}
	}

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) player.setVelocity(
				location.getDirection().multiply(push).add(vector));
	}

}
