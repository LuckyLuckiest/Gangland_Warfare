package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.IncendiaryData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.incendiary.IncendiaryWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;

/**
 * Parses the {@code Shoot:} section of an INCENDIARY weapon YAML and constructs an {@link IncendiaryWeapon}.
 */
public class IncendiaryWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public IncendiaryWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public IncendiaryWeapon parse(FileConfiguration config, ConfigurationSection shootSection,
	                              WeaponBaseData base) throws InvalidConfigurationException {
		if (shootSection == null)
			throw new InvalidConfigurationException("Shoot section not found for incendiary weapon");

		double coneAngle    = shootSection.getDouble("Cone_Angle", 30.0);
		double range        = shootSection.getDouble("Range", 5.0);
		int    tickRate     = shootSection.getInt("Rate", 2);
		int    fireDuration = shootSection.getInt("Fire_Duration", 60);
		int    consumeRate  = shootSection.getInt("Consume_Rate", 1);

		IncendiaryData incendiaryData = new IncendiaryData(coneAngle, range, fireDuration, tickRate, consumeRate);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		IncendiaryWeapon weapon = new IncendiaryWeapon(null, base.fileName(), base.displayName(), base.category(),
		                                               base.material(), base.durability(), base.lore(),
		                                               base.dropHologram(), base.deathMessages(), incendiaryData,
		                                               reloadData, ammunitionData);

		// Selective fire is optional for incendiary — defaults to AUTO + [auto] when absent so existing yml that
		// predates the selective-fire integration keeps the old "spray on right-click" behaviour.
		SelectiveFireSectionParser.ParsedSelectiveFire parsedSelectiveFire = SelectiveFireSectionParser.parse(
				shootSection, base.fileName());
		if (parsedSelectiveFire != null) {
			weapon.setCurrentSelectiveFire(parsedSelectiveFire.current());
			weapon.setAllowedSelectiveFires(parsedSelectiveFire.allowed());
		} else {
			weapon.setCurrentSelectiveFire(SelectiveFire.AUTO);
			weapon.setAllowedSelectiveFires(EnumSet.of(SelectiveFire.AUTO));
		}

		return weapon;
	}

}
