package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.reload.ReloadType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the {@code Ammunition:} and optional {@code Reload:} sections from a weapon YAML file. Returns a
 * {@link ParsedAmmo} record containing both the ammo DTO and the reload DTO, or {@code null} if the {@code Ammunition}
 * section is absent or the ammo type is not registered.
 */
public class AmmunitionSectionParser {

	private final AmmunitionManager ammunitionManager;

	public AmmunitionSectionParser(AmmunitionManager ammunitionManager) {
		this.ammunitionManager = ammunitionManager;
	}

	@Nullable
	public ParsedAmmo parse(FileConfiguration config) {
		ConfigurationSection ammoSection = config.getConfigurationSection("Ammunition");
		if (ammoSection == null) return null;

		String     ammoTypeString = ammoSection.getString("Ammo_Type");
		Ammunition ammoType       = ammoTypeString != null ? ammunitionManager.getAmmunition(ammoTypeString) : null;
		if (ammoType == null) return null;

		int capacity = ammoSection.getInt("Capacity", 0);
		int consume  = ammoSection.getInt("Consume", 1);
		int restore  = ammoSection.getInt("Restore", capacity);

		int        cooldown   = 0;
		ReloadType reloadType = ReloadType.getType("instant");

		ConfigurationSection reloadSection = config.getConfigurationSection("Reload");
		if (reloadSection != null) {
			cooldown = reloadSection.getInt("Cooldown", 0);

			String typeStr    = reloadSection.getString("Type", "instant");
			int    typeAmount = 1;
			if (typeStr.contains("-")) {
				String[] parts = typeStr.split("-");
				typeStr    = parts[0];
				typeAmount = Integer.parseInt(parts[1]);
			}
			reloadType = ReloadType.getType(typeStr);
			reloadType.setAmount(typeAmount);
		}

		AmmunitionData ammunitionData = new AmmunitionData(ammoType, capacity, consume, restore);
		ReloadData     reloadData     = ReloadData.builder().cooldown(cooldown).type(reloadType).build();
		return new ParsedAmmo(reloadData, ammunitionData);
	}

	public record ParsedAmmo(ReloadData reload, AmmunitionData ammo) { }

}
