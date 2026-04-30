package org.luckyraven.gangland.weapon.configuration.parser;

import lombok.CustomLog;
import org.bukkit.Color;
import org.bukkit.Material;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.MappingNode;
import org.luckyraven.gangland.persistence.config.NodeReader;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.dto.ModifiersData;
import org.luckyraven.gangland.weapon.modifiers.BreakMode;
import org.luckyraven.gangland.weapon.modifiers.action.*;
import org.luckyraven.gangland.weapon.util.BlockGroupResolver;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parses the {@code Modifiers:} section of a weapon YAML and attaches a {@link ModifiersData} to the given weapon.
 * Mirrors the static-utility shape of {@link SelectiveFireSectionParser} — the call site in {@link WeaponAddon} simply
 * delegates to {@link #apply(NodeReader, Weapon, ConfigReport)}.
 *
 * <p>Each modifier family uses its own custom DSL:
 * <ul>
 *   <li>{@code Break_Blocks}: list of {@code <block-group>-<hits>[-<mode>]} strings.</li>
 *   <li>{@code Penetration}: single {@code <block-count>-<damage-drop>-<knockback>} string.</li>
 *   <li>{@code Ricochet}: list of {@code <max-bounces>-<mat[,mat2,…]>-<damage-mult>} strings.</li>
 *   <li>{@code Tracer}: {@code <RRGGBB>-<fullLine>-<thickness>}.</li>
 *   <li>{@code Armor_Piercing}: a single double.</li>
 *   <li>{@code Flat_Damage}: a single double.</li>
 * </ul>
 */
@CustomLog
public final class ModifiersSectionParser {

	private ModifiersSectionParser() {
	}

	public static void apply(NodeReader root, Weapon weapon, ConfigReport report) {
		MappingNode modifiersSection = root.get("Modifiers").asMapping().orNull();
		if (modifiersSection == null) return;

		NodeReader modifiers = NodeReader.of(modifiersSection, report);

		weapon.setModifiersData(new ModifiersData());

		applyBreakBlocks(modifiers, weapon);
		applyPenetration(modifiers, weapon);
		applyRicochet(modifiers, weapon);
		applyTracer(modifiers, weapon);
		applyArmorPiercing(modifiers, weapon);
		applyFlatDamage(modifiers, weapon);
	}

	private static void applyBreakBlocks(NodeReader modifiers, Weapon weapon) {
		for (String entry : modifiers.get("Break_Blocks").asList().ofStrings().orEmpty()) {
			String[] parts = entry.split("-");
			if (parts.length != 2 && parts.length != 3) continue;
			try {
				Set<Material> materials = BlockGroupResolver.resolve(parts[0].trim());
				if (materials.isEmpty()) continue;

				int       hits = Integer.parseInt(parts[1].trim());
				BreakMode mode = BreakMode.RESTORE;
				if (parts.length == 3) {
					String token = parts[2].trim().toUpperCase(Locale.ROOT);
					try {
						mode = BreakMode.valueOf(token);
					} catch (IllegalArgumentException ex) {
						log.warn("Unknown break mode '{}' in weapon '{}', defaulting to RESTORE", parts[2],
						         weapon.getName());
					}
				}

				weapon.getModifiersData().addBreakBlock(new BlockBreakModifier(materials, hits, mode));
			} catch (NumberFormatException ignored) { }
		}
	}

	private static void applyPenetration(NodeReader modifiers, Weapon weapon) {
		String penetrationString = modifiers.get("Penetration").asString().orNull();
		if (penetrationString == null) return;

		String[] parts = penetrationString.split("-");
		if (parts.length != 3) return;

		try {
			weapon.getModifiersData()
			      .setPenetration(new PenetrationModifier(Integer.parseInt(parts[0].trim()),
			                                              Integer.parseInt(parts[1].trim()),
			                                              Double.parseDouble(parts[2].trim())));
		} catch (NumberFormatException ignored) { }
	}

	private static void applyRicochet(NodeReader modifiers, Weapon weapon) {
		for (String entry : modifiers.get("Ricochet").asList().ofStrings().orEmpty()) {
			String[] parts = entry.split("-");
			if (parts.length != 3) continue;

			try {
				Set<Material> bounceOffBlocks = new HashSet<>();
				for (String matName : parts[1].trim().split(","))
					bounceOffBlocks.addAll(BlockGroupResolver.resolve(matName.trim()));
				weapon.getModifiersData()
				      .addRicochet(new RicochetModifier(Integer.parseInt(parts[0].trim()), bounceOffBlocks,
				                                        Double.parseDouble(parts[2].trim())));
			} catch (NumberFormatException ignored) { }
		}
	}

	private static void applyTracer(NodeReader modifiers, Weapon weapon) {
		String tracerString = modifiers.get("Tracer").asString().orNull();
		if (tracerString == null) return;

		String[] parts = tracerString.split("-");
		if (parts.length != 3) return;

		try {
			String colorHex = parts[0].trim();
			Color color = Color.fromRGB(Integer.parseInt(colorHex.substring(0, 2), 16),
			                            Integer.parseInt(colorHex.substring(2, 4), 16),
			                            Integer.parseInt(colorHex.substring(4, 6), 16));
			weapon.getModifiersData()
			      .setTracer(new TracerModifier(color, Boolean.parseBoolean(parts[1].trim()),
			                                    Float.parseFloat(parts[2].trim())));
		} catch (NumberFormatException | IndexOutOfBoundsException ignored) { }
	}

	private static void applyArmorPiercing(NodeReader modifiers, Weapon weapon) {
		String armorPiercingString = modifiers.get("Armor_Piercing").asString().orNull();
		if (armorPiercingString == null) return;

		try {
			weapon.getModifiersData()
			      .setArmorPiercing(new ArmorPiercingModifier(Double.parseDouble(armorPiercingString.trim())));
		} catch (NumberFormatException ignored) { }
	}

	private static void applyFlatDamage(NodeReader modifiers, Weapon weapon) {
		String flatDamageString = modifiers.get("Flat_Damage").asString().orNull();
		if (flatDamageString == null) return;

		try {
			double bonus = Double.parseDouble(flatDamageString.trim());
			if (bonus > 0) weapon.getModifiersData().setFlatDamage(new FlatDamageModifier(bonus));
		} catch (NumberFormatException ignored) { }
	}

}
