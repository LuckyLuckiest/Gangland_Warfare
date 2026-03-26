package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ProjectileData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.projectile.ProjectileType;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

/**
 * Parses the {@code Shoot:} section of a GUN weapon YAML and constructs a {@link GunWeapon}. Ammunition is required for
 * guns — throws if the {@code Ammunition} section is absent or invalid.
 */
public class GunWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public GunWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public GunWeapon parse(FileConfiguration config, WeaponBaseData base) throws InvalidConfigurationException {
		ConfigurationSection shootSection = config.getConfigurationSection("Shoot");
		if (shootSection == null) throw new InvalidConfigurationException("Shoot section not found");

		String        selectiveFireString = shootSection.getString("Selective_Fire");
		SelectiveFire selectiveFire       = SelectiveFire.getType(Objects.requireNonNull(selectiveFireString));

		ConfigurationSection projectileSection = Objects.requireNonNull(
				shootSection.getConfigurationSection("Projectile"));
		int            projectileSpeed      = projectileSection.getInt("Speed");
		String         projectileTypeString = projectileSection.getString("Type");
		ProjectileType projectileType       = ProjectileType.getType(Objects.requireNonNull(projectileTypeString));

		ConfigurationSection damageSection = Objects.requireNonNull(
				projectileSection.getConfigurationSection("Damage"));
		int projectileDamage          = damageSection.getInt("Base");
		int projectileExplosionDamage = damageSection.getInt("Explosion_Damage");
		int projectileFireTicks       = damageSection.getInt("Fire_Ticks");
		int projectileHeadDamage      = damageSection.getInt("Head");

		ConfigurationSection criticalHitSection = damageSection.getConfigurationSection("Critical_Hit");
		int                  criticalHitChance  = 0;
		int                  criticalHitDamage  = 0;
		if (criticalHitSection != null) {
			criticalHitChance = criticalHitSection.getInt("Chance");
			criticalHitDamage = criticalHitSection.getInt("Amount");
		}

		int     projectileConsumed = projectileSection.getInt("Consumed_Amount");
		int     projectilePerShot  = projectileSection.getInt("Per_Shot");
		int     projectileCooldown = projectileSection.getInt("Cooldown");
		int     projectileDistance = projectileSection.getInt("Distance");
		boolean projectileParticle = projectileSection.getBoolean("Particle");

		ConfigurationSection weaponConsumedSection = Objects.requireNonNull(
				shootSection.getConfigurationSection("Weapon_Consumed"));
		int weaponConsumedOnShot = weaponConsumedSection.getInt("Consume_On_Shot");

		ParsedAmmo parsed = ammoParser.parse(config);
		if (parsed == null) throw new InvalidConfigurationException("Ammunition section not found or invalid");

		ReloadData     reloadData     = parsed.reload();
		AmmunitionData ammunitionData = parsed.ammo();

		ProjectileData projectileData = ProjectileData.builder()
		                                              .speed(projectileSpeed)
		                                              .type(projectileType)
		                                              .damage(projectileDamage)
		                                              .consumed(projectileConsumed)
		                                              .perShot(projectilePerShot)
		                                              .cooldown(projectileCooldown)
		                                              .distance(projectileDistance)
		                                              .particle(projectileParticle)
		                                              .build();

		GunWeapon gun = new GunWeapon(null, base.fileName(), base.displayName(), base.category(),
		                              base.material(), base.durability(), base.lore(), base.dropHologram(),
		                              base.deathMessages(), selectiveFire, weaponConsumedOnShot,
		                              projectileData, reloadData, ammunitionData);

		gun.getDamageData().setExplosionDamage(projectileExplosionDamage);
		gun.getDamageData().setFireTicks(projectileFireTicks);
		gun.getDamageData().setHeadDamage(projectileHeadDamage);
		gun.getDamageData().setCriticalHitChance(criticalHitChance);
		gun.getDamageData().setCriticalHitDamage(criticalHitDamage);

		return gun;
	}

}
