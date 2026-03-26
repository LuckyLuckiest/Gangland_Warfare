package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.BiologicalData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.biological.BiologicalWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

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
		double       areaRadius         = shootSection.getDouble("Area_Radius", 5.0);
		List<String> effectsPerLevel    = shootSection.getStringList("Effects_Per_Level");

		BiologicalData biologicalData = new BiologicalData(chargeTimePerLevel, maxChargeLevel, effectsPerLevel,
		                                                   areaRadius);
		ParsedAmmo     parsed         = ammoParser.parse(config);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new BiologicalWeapon(null, base.fileName(), base.displayName(), base.category(),
		                            base.material(), base.durability(), base.lore(), base.dropHologram(),
		                            base.deathMessages(), biologicalData, reloadData, ammunitionData);
	}

}
