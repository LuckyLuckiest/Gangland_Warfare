package me.luckyraven.weapon.configuration.parser;

import me.luckyraven.weapon.SelectiveFire;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Shared yml parser for the {@code Selective_Fire} and {@code Allowed_Modes} keys under a weapon's {@code Shoot:}
 * section. Used by every weapon type that supports selective fire (gun, incendiary, biological).
 *
 * <p>Schema:
 * <pre>
 * Shoot:
 *   Selective_Fire: single        # the starting fire mode
 *   Allowed_Modes:                # optional — modes the player may cycle through
 *     - single
 *     - auto
 * </pre>
 *
 * <p>If {@code Allowed_Modes} is omitted, the parser defaults to a singleton set containing only the configured
 * {@code Selective_Fire} (i.e. the weapon cannot cycle). The starting mode must always appear in {@code Allowed_Modes}
 * when both are specified — otherwise an {@link InvalidConfigurationException} is thrown.
 */
public final class SelectiveFireSectionParser {

	private SelectiveFireSectionParser() {
	}

	/**
	 * Parses the {@code Selective_Fire} and {@code Allowed_Modes} keys from the given shoot section. Returns
	 * {@code null} if neither key is present (caller treats this as "weapon has no selective fire").
	 */
	public static ParsedSelectiveFire parse(ConfigurationSection shootSection, String fileName)
			throws InvalidConfigurationException {
		if (shootSection == null) return null;

		String currentString = shootSection.getString("Selective_Fire");
		if (currentString == null) return null;

		SelectiveFire current = SelectiveFire.getType(currentString);

		Set<SelectiveFire> allowed;
		if (shootSection.isList("Allowed_Modes")) {
			List<String> rawList = shootSection.getStringList("Allowed_Modes");
			allowed = EnumSet.noneOf(SelectiveFire.class);
			for (String raw : rawList) {
				allowed.add(SelectiveFire.getType(raw));
			}

			if (allowed.isEmpty()) {
				throw new InvalidConfigurationException(
						"Weapon '" + fileName + "' has an empty Allowed_Modes list — list at least one mode");
			}
			if (!allowed.contains(current)) {
				throw new InvalidConfigurationException(
						"Weapon '" + fileName + "' has Selective_Fire: " + currentString +
						" but '" + currentString + "' is not in Allowed_Modes " + allowed);
			}
		} else {
			allowed = EnumSet.of(current);
		}

		return new ParsedSelectiveFire(current, allowed);
	}

	public record ParsedSelectiveFire(SelectiveFire current, Set<SelectiveFire> allowed) {
	}

}
