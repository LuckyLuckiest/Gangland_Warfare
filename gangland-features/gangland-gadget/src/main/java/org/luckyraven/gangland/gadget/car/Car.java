package org.luckyraven.gangland.gadget.car;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.gangland.item.fuel.FuelKey;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
public class Car {

	// Identity
	private final String       carId;
	private final String       displayName;
	private final Material     itemMaterial;
	private final int          customModelData;
	private final List<String> lore;

	// Vehicle physics
	private final double maxSpeed;
	private final double acceleration;
	private final double deceleration;
	private final double turnSpeed;
	private final double maxHealth;

	// Fuel
	private final boolean fuelEnabled;
	private final String  fuelKey;
	private final int     maxFuel;

	// Repair
	private final int maxDurability;

	/**
	 * Placeholder resolver injected by {@code CarAddon} via the builder so {@link #buildItem(Player)} can resolve
	 * {@code %gangland_*%} tokens in the display name and lore.
	 */
	@Nullable
	private final Placeholder placeholder;

	/**
	 * Checks whether the given ItemStack is a car item by looking for the car NBT tag.
	 */
	public static boolean isCarItem(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		return new ItemBuilder(item).hasNBTTag(CarKey.CAR_ID.getKey());
	}

	/**
	 * Extracts the car configuration key from an ItemStack, or {@code null} if it is not a car item.
	 */
	@Nullable
	public static String getCarId(@Nullable ItemStack item) {
		if (!isCarItem(item)) {
			return null;
		}
		return new ItemBuilder(item).getStringTagData(CarKey.CAR_ID.getKey());
	}

	/**
	 * Returns the permission node for this car, derived from its configuration key.
	 *
	 * @return {@code "gangland.cars.<carId>"}
	 */
	public String getPermission() {
		return "gangland.cars." + carId;
	}

	/**
	 * Builds an ItemStack representing this car in inventory form, stamped with identifying NBT tags.
	 */
	public ItemStack buildItem() {
		return buildItem(null);
	}

	public ItemStack buildItem(@Nullable Player player) {
		ItemBuilder builder = new ItemBuilder(itemMaterial);
		builder.setDisplayName(resolvePlaceholder(player, displayName));

		List<String> resolvedLore = resolvePlaceholder(player, lore);
		if (resolvedLore != null && !resolvedLore.isEmpty()) {
			builder.setLore(resolvedLore);
		}

		if (customModelData > 0) {
			builder.setCustomModelData(customModelData);
		}

		builder.addTag(CarKey.CAR_ID.getKey(), carId);
		builder.addTag(CarKey.CAR_DURABILITY.getKey(), maxDurability);
		builder.addTag(CarKey.CAR_MAX_DURABILITY.getKey(), maxDurability);

		if (fuelEnabled && fuelKey != null && !fuelKey.isEmpty() && maxFuel > 0) {
			builder.addTag(FuelKey.FUEL_ID.getKey(), fuelKey);
			builder.addTag(FuelKey.FUEL_CURRENT.getKey(), maxFuel);
			builder.addTag(FuelKey.FUEL_MAX.getKey(), maxFuel);
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
