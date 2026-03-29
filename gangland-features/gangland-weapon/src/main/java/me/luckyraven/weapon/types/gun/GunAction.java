package me.luckyraven.weapon.types.gun;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.dto.SoundData;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GunAction {

	private final JavaPlugin          plugin;
	private final WeaponService       weaponService;
	private final GunWeapon           weapon;
	private final RecoilCompatibility recoilCompatibility;

	public GunAction(JavaPlugin plugin, WeaponService weaponService, GunWeapon weapon,
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
		weapon.applyPush(shooter);

		// shooting sound — echo broadcasts to all players near the shooter's location
		SoundConfiguration.playSoundsAtLocation(shooter.getLocation(), weapon.getSoundData().getShotCustom(),
		                                        weapon.getSoundData().getShotDefault());
	}

}
