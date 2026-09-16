package org.luckyraven.gangland.gadget.jetpack;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.Placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Domain entity for a gadget-owned jetpack, mirroring {@code car.Car} exactly. Rehomed off Bartizan's
 * {@code Wearable} per WS7 — see {@code brainstorming/gadget-wave-2026-09-16/plans/WS7-gadget-ownership.md}.
 */
@Builder
@Getter
public class Jetpack {

	// Identity
	private final String       jetpackId;
	private final String       displayName;
	private final Material     material;
	private final int          customModelData;
	private final List<String> lore;

	// Fuel
	private final String fuelKey;
	private final int    maxFuel;

	// Physics — read directly by JetpackTask (G3) off this config object, never re-parsed per-item.
	private final double ascendPower;
	private final double maxSpeedY;
	private final int    fuelConsumptionRate;

	/**
	 * The raw parsed {@code Sounds:} block ({@code {Thrust: {...}, Glide: {...}}}), read later by G3's
	 * {@code JetpackTask.soundTag()}. Nullable — a jetpack with no {@code Sounds:} section simply has none.
	 */
	@Nullable
	private final Map<String, Object> sounds;

	/**
	 * True only when this jetpack's id was actually registered with Bartizan's {@code WearableCatalog}
	 * (WS7-D4 bridge, {@link org.luckyraven.gangland.gadget.jetpack.config.JetpackBartizanTraitBridge}). Drives
	 * whether {@link #buildItem()} stamps the {@code wearable} tag.
	 */
	@Builder.Default
	private final boolean bartizanRegistered = false;

	/**
	 * Placeholder resolver injected by {@code JetpackAddon} via the builder so {@link #buildItem(Player)} can
	 * resolve {@code %gangland_*%} tokens in the display name and lore.
	 */
	@Nullable
	private final Placeholder placeholder;

	/**
	 * Checks whether the given ItemStack is a jetpack item by looking for the jetpack NBT tag.
	 */
	public static boolean isJetpackItem(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		return new ItemBuilder(item).hasNBTTag(JetpackKey.JETPACK_ID.getKey());
	}

	/**
	 * Extracts the jetpack configuration key from an ItemStack, or {@code null} if it is not a jetpack item.
	 */
	@Nullable
	public static String getJetpackId(@Nullable ItemStack item) {
		if (!isJetpackItem(item)) {
			return null;
		}
		return new ItemBuilder(item).getStringTagData(JetpackKey.JETPACK_ID.getKey());
	}

	/**
	 * Returns the permission node for this jetpack, derived from its configuration key.
	 *
	 * @return {@code "gangland.jetpacks.<jetpackId>"}
	 */
	public String getPermission() {
		return "gangland.jetpacks." + jetpackId;
	}

	/**
	 * Builds an ItemStack representing this jetpack in inventory form, stamped with identifying NBT tags.
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

		builder.addTag(JetpackKey.JETPACK_ID.getKey(), jetpackId);

		builder.addTag(FuelKey.FUEL_ID.getKey(), fuelKey);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), maxFuel);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), maxFuel);

		// Only stamped when JetpackBartizanTraitBridge actually registered this id with Bartizan's
		// WearableCatalog (WS7-D4), so Bartizan's damage-reduction path resolves it. Never stamped otherwise —
		// stamping it unconditionally would claim Bartizan's "wearable" vocabulary for an item Bartizan never
		// actually registered a definition for.
		if (bartizanRegistered) {
			builder.addTag("wearable", jetpackId);
		}

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
