package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.persistence.config.Severity;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.reload.ReloadType;
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
	public ParsedAmmo parse(NodeReader root, ConfigReport report) {
		MappingNode ammoSection = root.get("Ammunition").asMapping().orNull();
		if (ammoSection == null) return null;

		NodeReader ammo = NodeReader.of(ammoSection, report);

		// Read all admin-facing ammo/reload keys unconditionally, BEFORE deciding whether the ammo type resolves.
		// Bailing early on an unregistered ammo type would leave these keys untouched and surface them as spurious
		// unknown-key warnings, even though the admin is authoring a coherent config.
		String ammoTypeString = ammo.get("Ammo_Type").asString().required().orNull();
		int    capacity       = ammo.get("Capacity").asInt().min(0).orDefault(0);
		int    consume        = ammo.get("Consume").asInt().min(0).orDefault(1);
		int    restore        = ammo.get("Restore").asInt().min(0).orDefault(capacity);

		int        cooldown   = 0;
		ReloadType reloadType = ReloadType.getType("instant");

		MappingNode reloadSection = root.get("Reload").asMapping().orNull();
		if (reloadSection != null) {
			NodeReader reload = NodeReader.of(reloadSection, report);

			cooldown = reload.get("Cooldown").asInt().min(0).orDefault(0);

			String typeStr    = reload.get("Type").asString().orDefault("instant");
			int    typeAmount = 1;
			if (typeStr.contains("-")) {
				String[] parts = typeStr.split("-");
				typeStr = parts[0];
				try {
					typeAmount = Integer.parseInt(parts[1]);
				} catch (NumberFormatException ignored) { }
			}
			reloadType = ReloadType.getType(typeStr);
			reloadType.setAmount(typeAmount);
		}

		Ammunition ammoType = ammoTypeString != null ? ammunitionManager.getAmmunition(ammoTypeString) : null;
		if (ammoType == null) {
			if (ammoTypeString != null) {
				report.add(Severity.ERROR, ammoSection.location(), "Ammunition.Ammo_Type",
				           "unknown ammo type '" + ammoTypeString + "'", "ammo.unknown_type");
			}
			return null;
		}

		AmmunitionData ammunitionData = new AmmunitionData(ammoType, capacity, consume, restore);
		ReloadData     reloadData     = ReloadData.builder().cooldown(cooldown).type(reloadType).build();
		return new ParsedAmmo(reloadData, ammunitionData);
	}

	public record ParsedAmmo(ReloadData reload, AmmunitionData ammo) { }

}
