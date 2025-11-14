package me.luckyraven.weapon.repair.config;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Data class representing a repair item configuration.
 */
@Getter
@Builder
public class RepairItemData {

	@NotNull
	private final String id;

	@NotNull
	private final String displayName;

	@NotNull
	private final Material material;

	private final int level;
	private final int durability;

	@NotNull
	private final List<String> lore;

	@NotNull
	private final List<ConfigurationSection> effects;

	@NotNull
	private final Map<String, Object> metadata;

	private final int customModelData;

	@Override
	public String toString() {
		return String.format("RepairItemData{id='%s', displayName='%s', material=%s, level=%d, durability=%d}",
							 id, displayName, material, level, durability);
	}
}