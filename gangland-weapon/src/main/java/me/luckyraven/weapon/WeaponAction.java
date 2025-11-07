package me.luckyraven.weapon;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.events.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
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
		if (weapon.isBroken()) {
			SoundConfiguration.playSounds(shooter, weapon.getEmptyMagCustomSound(), weapon.getEmptyMagDefaultSound());

			ChatUtil.sendActionBar(shooter, "&cBroken");
			return;
		}

		// consume a bullet
		boolean consumed = weapon.consumeShot();

		// no shot fired
		if (!consumed) {
			// empty magazine sound
			SoundConfiguration.playSounds(shooter, weapon.getEmptyMagCustomSound(), weapon.getEmptyMagDefaultSound());
			return;
		}

		WeaponProjectile<?> weaponProjectile = weapon.getProjectileType().createInstance(plugin, shooter, weapon);
		WeaponShootEvent    weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);
		plugin.getServer().getPluginManager().callEvent(weaponShootEvent);

		// launch the projectile
		if (weaponShootEvent.isCancelled()) {
			// substitute for the consumed shot
			weapon.addAmmunition(1);
			return;
		}

		weaponProjectile.launchProjectile();

		weapon.updateWeaponData(heldWeapon);

		// change durability of the weapon
		short durabilityOnShot = weapon.getDurabilityOnShot();
		if (durabilityOnShot > (short) 0) {
			weapon.decreaseDurability(heldWeapon);
		}

		weapon.updateWeapon(shooter, heldWeapon, shooter.getInventory().getHeldItemSlot());

		// apply recoil
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
