package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.BiologicalData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.biological.BiologicalWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;

/**
 * Parses the {@code Shoot:} section of a BIOLOGICAL weapon YAML and constructs a {@link BiologicalWeapon}.
 */
public class BiologicalWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public BiologicalWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public BiologicalWeapon parse(FileConfiguration config, ConfigurationSection shootSection,
	                              WeaponBaseData base) throws InvalidConfigurationException {
		if (shootSection == null)
			throw new InvalidConfigurationException("Shoot section not found for biological weapon");

		int          chargeTimePerLevel = shootSection.getInt("Charge_Time_Per_Level", 20);
		int          maxChargeLevel     = shootSection.getInt("Max_Charge_Level", 3);
		double       range              = shootSection.getDouble("Range", 30.0);
		double       baseDamage         = shootSection.getDouble("Base_Damage", 4.0);
		List<String> effectsPerLevel    = shootSection.getStringList("Effects_Per_Level");

		BiologicalData biologicalData = new BiologicalData(chargeTimePerLevel, maxChargeLevel, effectsPerLevel,
		                                                   range, baseDamage);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		BiologicalWeapon weapon = new BiologicalWeapon(null, base.fileName(), base.displayName(), base.category(),
		                                               base.material(), base.durability(), base.lore(),
		                                               base.dropHologram(), base.deathMessages(), biologicalData,
		                                               reloadData, ammunitionData);

		// Selective fire is optional for biological. The actual mechanic is the same regardless of mode (charge then
		// release), but the field is set so the listener doesn't NPE on Shift-F. Default = SINGLE locked.
		SelectiveFireSectionParser.ParsedSelectiveFire parsedSelectiveFire = SelectiveFireSectionParser.parse(
				shootSection, base.fileName());
		if (parsedSelectiveFire != null) {
			weapon.setCurrentSelectiveFire(parsedSelectiveFire.current());
			weapon.setAllowedSelectiveFires(parsedSelectiveFire.allowed());
		} else {
			weapon.setCurrentSelectiveFire(SelectiveFire.SINGLE);
			weapon.setAllowedSelectiveFires(EnumSet.of(SelectiveFire.SINGLE));
		}

		return weapon;
	}

}
