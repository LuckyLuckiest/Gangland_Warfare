package org.luckyraven.gangland.gadget.grapple;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.Placeholder;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain entity for a gadget-owned grappling hook, mirroring {@code jetpack.Jetpack} (itself mirroring
 * {@code car.Car}) — see {@code brainstorming/gadget-wave-2026-09-16/plans/WS8-gadget-catalogue.md}. Cooldown
 * only (WS8-D1): no fuel/durability fields, no Bartizan bridge.
 */
@Builder
@Getter
public class Grapple {

	// Identity
	private final String       grappleId;
	private final String       displayName;
	private final Material     material;
	private final int          customModelData;
	private final List<String> lore;

	// Mechanics — read directly by GrappleService/GrappleLaunchListener (G2/G3) off this config object, never
	// re-parsed per-item.
	private final int     maxDistance;
	private final double  maxPullSpeed;
	private final double  pullAcceleration;
	private final double  arrivalDistance;
	private final int     cooldownSeconds;
	private final int     maxDurationTicks;
	private final int     fallDamageGraceTicks;
	private final boolean requireLineOfSight;

	/**
	 * Placeholder resolver injected by {@code GrappleAddon} via the builder so {@link #buildItem(Player)} can
	 * resolve {@code %gangland_*%} tokens in the display name and lore.
	 */
	@Nullable
	private final Placeholder placeholder;

	/**
	 * Checks whether the given ItemStack is a grapple item by looking for the grapple NBT tag.
	 */
	public static boolean isGrappleItem(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		return new ItemBuilder(item).hasNBTTag(GrappleKey.GRAPPLE_ID.getKey());
	}

	/**
	 * Extracts the grapple configuration key from an ItemStack, or {@code null} if it is not a grapple item.
	 */
	@Nullable
	public static String getGrappleId(@Nullable ItemStack item) {
		if (!isGrappleItem(item)) {
			return null;
		}
		return new ItemBuilder(item).getStringTagData(GrappleKey.GRAPPLE_ID.getKey());
	}

	/**
	 * Returns the permission node for this grapple, derived from its configuration key.
	 *
	 * @return {@code "gangland.grapples.<grappleId>"}
	 */
	public String getPermission() {
		return "gangland.grapples." + grappleId;
	}

	/**
	 * Builds an ItemStack representing this grapple in inventory form, stamped with identifying NBT tags.
	 */
	public ItemStack buildItem() {
		return buildItem(null);
	}

	public ItemStack buildItem(@Nullable Player player) {
		ItemBuilder builder = new ItemBuilder(material);
		builder.setDisplayName(resolvePlaceholder(player, displayName));

		List<String> resolvedLore = resolvePlaceholder(player, lore);
		if (resolvedLore != null && !resolvedLore.isEmpty()) {
			builder.setLore(resolvedLore);
		}

		if (customModelData > 0) {
			builder.setCustomModelData(customModelData);
		}

		builder.addTag(GrappleKey.GRAPPLE_ID.getKey(), grappleId);

		return builder.build();
	}

	private String resolvePlaceholder(@Nullable Player player, @Nullable String text) {
		if (text == null || text.isEmpty() || placeholder == null) return text;
		return placeholder.convert(player, text);
	}

	private List<String> resolvePlaceholder(@Nullable Player player, @Nullable List<String> loreLines) {
		if (loreLines == null || loreLines.isEmpty() || placeholder == null) return loreLines;
		List<String> resolved = new ArrayList<>(loreLines.size());
		for (String line : loreLines) {
			resolved.add(line == null ? null : placeholder.convert(player, line));
		}
		return resolved;
	}
}
