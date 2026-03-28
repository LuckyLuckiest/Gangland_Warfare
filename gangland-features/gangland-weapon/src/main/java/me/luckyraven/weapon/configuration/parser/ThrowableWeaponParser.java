package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.dto.ThrowableData;
import me.luckyraven.weapon.types.throwable.ThrowableWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Parses the {@code Throw:} / {@code Shoot:} section of a THROWABLE weapon YAML and constructs a
 * {@link ThrowableWeapon}.
 */
public class ThrowableWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public ThrowableWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public ThrowableWeapon parse(FileConfiguration config, ConfigurationSection shootSection,
	                             WeaponBaseData base) throws InvalidConfigurationException {
		if (shootSection == null)
			throw new InvalidConfigurationException("Throw/Shoot section not found for throwable weapon");

		int     fuseTime        = shootSection.getInt("Fuse_Time", 60);
		double  explosionRadius = shootSection.getDouble("Explosion_Radius", 3.0);
		int     explosionDamage = shootSection.getInt("Explosion_Damage", 6);
		int     fireTicks       = shootSection.getInt("Fire_Ticks", 0);
		boolean bounces         = shootSection.getBoolean("Bounces", false);
		int     maxBounces      = shootSection.getInt("Max_Bounces", 5);
		boolean sticky          = shootSection.getBoolean("Sticky", false);
		String  entityType      = shootSection.getString("Entity_Type", "SNOWBALL");

		if (bounces && sticky)
			throw new InvalidConfigurationException("Throwable cannot have both Bounces and Sticky enabled");

		ThrowableData throwableData = new ThrowableData(fuseTime, explosionRadius, explosionDamage, fireTicks,
		                                                bounces, maxBounces, sticky, entityType);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new ThrowableWeapon(null, base.fileName(), base.displayName(), base.category(),
		                           base.material(), base.durability(), base.lore(), base.dropHologram(),
		                           base.deathMessages(), throwableData, reloadData, ammunitionData);
	}

}
