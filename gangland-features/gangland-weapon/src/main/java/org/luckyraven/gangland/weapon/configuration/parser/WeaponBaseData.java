package org.luckyraven.gangland.weapon.configuration.parser;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.util.List;

/**
 * Holds the common fields parsed from the {@code Information:} section, shared across all weapon types.
 */
public record WeaponBaseData(
		String fileName,
		String displayName,
		WeaponType category,
		Material material,
		int customModelData,
		short durability,
		List<String> lore,
		boolean dropHologram,
		@Nullable List<String> deathMessages
) { }
