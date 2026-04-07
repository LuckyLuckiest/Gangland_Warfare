package me.luckyraven.weapon.types.gun;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ActionBarManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.dto.SoundData;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponShooting;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GunAction {

	private final JavaPlugin          plugin;
	private final WeaponService       weaponService;
	private final GunWeapon           weapon;
	private final RecoilCompatibility recoilCompatibility;
	private final WeaponRaytracer     raytracer;

	public GunAction(JavaPlugin plugin, WeaponService weaponService, GunWeapon weapon,
	                 RecoilCompatibility recoilCompatibility, WeaponRaytracer raytracer) {
		this.plugin              = plugin;
		this.weaponService       = weaponService;
		this.weapon              = weapon;
		this.recoilCompatibility = recoilCompatibility;
		this.raytracer           = raytracer;
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

			ActionBarManager.send(shooter, "&cBroken");
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

		// All projectile types now flow through the unified raytracer via WeaponShooting.
		// Hitscan (BULLET, SPREAD) uses fireInstant; slow visual projectiles (ROCKET, FLARE)
		// use a per-tick SteppedProjectileTask that drives a cosmetic Bukkit entity.
		WeaponShootEvent shootEvent = new WeaponShootEvent(weapon, shooter);
		Bukkit.getPluginManager().callEvent(shootEvent);

		if (shootEvent.isCancelled()) {
			weapon.addAmmunition(1);
			return;
		}

		WeaponShooting.fire(plugin, raytracer, shooter, weapon);

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
		weapon.applyPush(shooter);

		// shooting sound — echo broadcasts to all players near the shooter's location
		SoundConfiguration.playSoundsAtLocation(shooter.getLocation(), weapon.getSoundData().getShotCustom(),
		                                        weapon.getSoundData().getShotDefault());
	}

}
