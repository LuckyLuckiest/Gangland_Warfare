package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.MeleeData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.melee.MeleeWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Parses the {@code Attack:} / {@code Shoot:} section of a MELEE weapon YAML and constructs a {@link MeleeWeapon}.
 */
public class MeleeWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public MeleeWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public MeleeWeapon parse(FileConfiguration config, ConfigurationSection shootSection,
	                         WeaponBaseData base) throws InvalidConfigurationException {
		if (shootSection == null)
			throw new InvalidConfigurationException("Attack/Shoot section not found for melee weapon");

		double damage    = shootSection.getDouble("Damage", 4.0);
		double range     = shootSection.getDouble("Range", 2.5);
		int    cooldown  = shootSection.getInt("Cooldown", 10);
		double knockback = shootSection.getDouble("Knockback", 0.5);

		MeleeData      meleeData      = new MeleeData(damage, range, cooldown, knockback);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new MeleeWeapon(null, base.fileName(), base.displayName(), base.category(),
		                       base.material(), base.durability(), base.lore(), base.dropHologram(),
		                       base.deathMessages(), meleeData, reloadData, ammunitionData);
	}

}
