package me.luckyraven.weapon.util;

import org.bukkit.Material;

import java.util.*;

/**
 * Resolves block material groups (e.g., GLASS includes all glass colors).
 */
public final class BlockGroupResolver {

	private static final Map<String, Set<Material>> BLOCK_GROUPS = new HashMap<>();

	static {
		// Glass group
		Set<Material> glassGroup = new HashSet<>();
		glassGroup.add(Material.GLASS);
		for (Material mat : Material.values()) {
			String name = mat.name();
			if (name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE") || name.equals("GLASS_PANE") ||
				name.contains("STAINED_GLASS")) {
				glassGroup.add(mat);
			}
		}
		BLOCK_GROUPS.put("GLASS", glassGroup);

		// Terracotta group
		Set<Material> terracottaGroup = new HashSet<>();
		for (Material mat : Material.values()) {
			if (mat.name().contains("TERRACOTTA")) {
				terracottaGroup.add(mat);
			}
		}
		BLOCK_GROUPS.put("TERRACOTTA", terracottaGroup);

		// Wool group
		Set<Material> woolGroup = new HashSet<>();
		for (Material mat : Material.values()) {
			if (mat.name().endsWith("_WOOL") || mat.name().equals("WOOL")) {
				woolGroup.add(mat);
			}
		}
		BLOCK_GROUPS.put("WOOL", woolGroup);

		// Concrete group
		Set<Material> concreteGroup = new HashSet<>();
		for (Material mat : Material.values()) {
			if (mat.name().contains("CONCRETE")) {
				concreteGroup.add(mat);
			}
		}
		BLOCK_GROUPS.put("CONCRETE", concreteGroup);

		// Ice group
		Set<Material> iceGroup = new HashSet<>();
		iceGroup.add(Material.ICE);
		iceGroup.add(Material.PACKED_ICE);
		iceGroup.add(Material.BLUE_ICE);
		iceGroup.add(Material.FROSTED_ICE);
		BLOCK_GROUPS.put("ICE", iceGroup);
	}

	private BlockGroupResolver() { }

	/**
	 * Resolves a material name to a set of materials. If the name matches a group, returns all materials in that group.
	 * Otherwise, attempts to match a single material.
	 *
	 * @param materialName The material or group name
	 *
	 * @return Set of matching materials, empty if none found
	 */
	public static Set<Material> resolve(String materialName) {
		String upperName = materialName.toUpperCase();

		// Check if it's a group
		if (BLOCK_GROUPS.containsKey(upperName)) {
			return new HashSet<>(BLOCK_GROUPS.get(upperName));
		}

		// Try to match single material
		try {
			Material material = Material.valueOf(upperName);
			return Set.of(material);
		} catch (IllegalArgumentException e) {
			return Collections.emptySet();
		}
	}

	/**
	 * Checks if a material name represents a group.
	 */
	public static boolean isGroup(String materialName) {
		return BLOCK_GROUPS.containsKey(materialName.toUpperCase());
	}

}
