package org.luckyraven.gangland.weapon.configuration.parser;

import org.bukkit.configuration.InvalidConfigurationException;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.parser.AmmunitionSectionParser.ParsedAmmo;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.MeleeData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.types.melee.MeleeWeapon;

/**
 * Parses the {@code Attack:} / {@code Shoot:} section of a MELEE weapon YAML and constructs a {@link MeleeWeapon}.
 */
public class MeleeWeaponParser {

	private final AmmunitionSectionParser ammoParser;

	public MeleeWeaponParser(AmmunitionManager ammunitionManager) {
		this.ammoParser = new AmmunitionSectionParser(ammunitionManager);
	}

	public MeleeWeapon parse(NodeReader root, NodeReader shoot, ConfigReport report, WeaponBaseData base)
			throws InvalidConfigurationException {
		if (shoot == null) {
			throw new InvalidConfigurationException("Attack/Shoot section not found for melee weapon");
		}

		double damage    = shoot.get("Damage").asDouble().min(0).required().orDefault(4.0);
		double range     = shoot.get("Range").asDouble().min(0).required().orDefault(2.5);
		int    cooldown  = shoot.get("Cooldown").asInt().min(0).orDefault(10);
		double knockback = shoot.get("Knockback").asDouble().orDefault(0.5);

		MeleeData      meleeData      = new MeleeData(damage, range, cooldown, knockback);
		ParsedAmmo     parsed         = ammoParser.parse(root, report);
		ReloadData     reloadData     = parsed != null ? parsed.reload() : null;
		AmmunitionData ammunitionData = parsed != null ? parsed.ammo() : null;

		return new MeleeWeapon(null, base.fileName(), base.displayName(), base.category(),
		                       base.material(), base.customModelData(), base.durability(), base.lore(),
		                       base.dropHologram(), base.deathMessages(), meleeData, reloadData, ammunitionData);
	}

}
