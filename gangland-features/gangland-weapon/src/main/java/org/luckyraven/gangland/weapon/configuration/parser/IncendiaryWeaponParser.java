package org.luckyraven.gangland.weapon.configuration.parser;

import org.bukkit.configuration.InvalidConfigurationException;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.NodeReader;
import org.luckyraven.gangland.weapon.SelectiveFire;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.IncendiaryData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.types.incendiary.IncendiaryWeapon;

import java.util.EnumSet;

/**
 * Parses the {@code Shoot:} section of an INCENDIARY weapon YAML and constructs an {@link IncendiaryWeapon}.
 */
public class IncendiaryWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public IncendiaryWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public IncendiaryWeapon parse(NodeReader root, NodeReader shoot, ConfigReport report, WeaponBaseData base)
			throws InvalidConfigurationException {
		if (shoot == null) {
			throw new InvalidConfigurationException("Shoot section not found for incendiary weapon");
		}

		double coneAngle    = shoot.get("Cone_Angle").asDouble().min(0).orDefault(30.0);
		double range        = shoot.get("Range").asDouble().min(0).orDefault(5.0);
		int    tickRate     = shoot.get("Rate").asInt().min(1).orDefault(2);
		int    fireDuration = shoot.get("Fire_Duration").asInt().min(0).orDefault(60);
		int    consumeRate  = shoot.get("Consume_Rate").asInt().min(0).orDefault(1);

		IncendiaryData incendiaryData = new IncendiaryData(coneAngle, range, fireDuration, tickRate, consumeRate);
		ParsedAmmo     parsed         = ammoParser.parse(root, report);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		IncendiaryWeapon weapon = new IncendiaryWeapon(null, base.fileName(), base.displayName(), base.category(),
		                                               base.material(), base.customModelData(), base.durability(),
		                                               base.lore(), base.dropHologram(), base.deathMessages(),
		                                               incendiaryData, reloadData, ammunitionData);

		SelectiveFireSectionParser.ParsedSelectiveFire parsedSelectiveFire =
				SelectiveFireSectionParser.parse(shoot, report, base.fileName());
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
