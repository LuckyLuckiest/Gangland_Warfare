package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.BiologicalData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.biological.BiologicalWeapon;
import org.bukkit.configuration.InvalidConfigurationException;

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

	public BiologicalWeapon parse(NodeReader root, NodeReader shoot, ConfigReport report, WeaponBaseData base)
			throws InvalidConfigurationException {
		if (shoot == null) {
			throw new InvalidConfigurationException("Shoot section not found for biological weapon");
		}

		int          chargeTimePerLevel = shoot.get("Charge_Time_Per_Level").asInt().min(1).orDefault(20);
		int          maxChargeLevel     = shoot.get("Max_Charge_Level").asInt().min(1).orDefault(3);
		double       range              = shoot.get("Range").asDouble().min(0).orDefault(30.0);
		double       baseDamage         = shoot.get("Base_Damage").asDouble().min(0).orDefault(4.0);
		List<String> effectsPerLevel    = shoot.get("Effects_Per_Level").asList().ofStrings().orEmpty();

		BiologicalData biologicalData = new BiologicalData(chargeTimePerLevel, maxChargeLevel, effectsPerLevel,
		                                                   range, baseDamage);
		ParsedAmmo     parsed         = ammoParser.parse(root, report);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		BiologicalWeapon weapon = new BiologicalWeapon(null, base.fileName(), base.displayName(), base.category(),
		                                               base.material(), base.customModelData(), base.durability(),
		                                               base.lore(), base.dropHologram(), base.deathMessages(),
		                                               biologicalData, reloadData, ammunitionData);

		SelectiveFireSectionParser.ParsedSelectiveFire parsedSelectiveFire =
				SelectiveFireSectionParser.parse(shoot, report, base.fileName());
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
