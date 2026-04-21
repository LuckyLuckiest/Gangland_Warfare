package me.luckyraven.weapon.types.gun;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.configuration.SoundConfiguration;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponShooting;
import me.luckyraven.weapon.util.EmptyMagSoundGate;
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
		if (weapon.isBroken()) {
			EmptyMagSoundGate.play(plugin, shooter, weapon);
			ActionBarManager.send(shooter, "&cBroken");
			return;
		}

		// consume a bullet
		boolean consumed = weapon.consumeShot();

		// no shot fired
		if (!consumed) {
			// empty magazine sound — gated so burst/auto modes play it only once per press cycle
			EmptyMagSoundGate.play(plugin, shooter, weapon);
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

		if (weapon.getRecoilData() != null) {
			weapon.getRecoil().applyRecoil(recoilCompatibility, shooter);
			weapon.applyPush(shooter);
		}

		// shooting sound — echo broadcasts to all players near the shooter's location
		SoundConfiguration.playSoundsAtLocation(shooter.getLocation(), weapon.getSoundData().getShotCustom(),
		                                        weapon.getSoundData().getShotDefault());
	}

}
