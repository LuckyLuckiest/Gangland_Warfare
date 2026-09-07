package org.luckyraven.gangland.item.fuel;

import com.cryptomorin.xseries.XMaterial;
import lombok.Builder;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.unique.UniqueItem;

/**
 * Represents a fuel component that can be attached to a {@link UniqueItem}. Fuel items are identified by a
 * {@link #fuelKey} and track their current fuel level via NBT tags.
 *
 * <p>The fuel system is generic and reusable across different gadgets (cars, jetpacks, etc.).
 * Each gadget references a fuel key to determine which fuel item it consumes from the player's inventory.
 */
@Builder
@Getter
public class Fuel {

	/**
	 * Unique identifier for this fuel type (e.g. "gasoline"). Gadgets reference this key to specify which fuel they
	 * consume.
	 */
	private final String fuelKey;

	/**
	 * Maximum fuel capacity in ticks.
	 */
	private final int maxFuel;

	/**
	 * Display name for the fuel type (e.g. "&6Gasoline").
	 */
	private final String displayName;

	/**
	 * The material used to refuel this item (e.g. COAL). Right-clicking the fuel item with this material adds fuel.
	 */
	private final XMaterial fuelMaterial;

	/**
	 * How many ticks of fuel are added per unit of {@link #fuelMaterial} consumed.
	 */
	private final int fuelPerItem;

	// =========================================================================
	// Static NBT helpers
	// =========================================================================

	/**
	 * Returns whether the given ItemStack is a fuel item (has the fuel NBT tag).
	 */
	public static boolean isFuelItem(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		return new ItemBuilder(item).hasNBTTag(FuelKey.FUEL_ID.getKey());
	}

	/**
	 * Reads the fuel key from an ItemStack's NBT.
	 *
	 * @return the fuel key, or {@code null} if the item is not a fuel item
	 */
	@Nullable
	public static String getFuelKey(@Nullable ItemStack item) {
		if (!isFuelItem(item)) return null;
		return new ItemBuilder(item).getStringTagData(FuelKey.FUEL_ID.getKey());
	}

	/**
	 * Reads the current fuel level from an ItemStack's NBT.
	 *
	 * @return the current fuel, or 0 if the item is not a fuel item
	 */
	public static int getCurrentFuel(@Nullable ItemStack item) {
		if (!isFuelItem(item)) return 0;
		return new ItemBuilder(item).getIntegerTagData(FuelKey.FUEL_CURRENT.getKey());
	}

	/**
	 * Reads the maximum fuel capacity from an ItemStack's NBT.
	 *
	 * @return the max fuel, or 0 if the item is not a fuel item
	 */
	public static int getMaxFuel(@Nullable ItemStack item) {
		if (!isFuelItem(item)) return 0;
		return new ItemBuilder(item).getIntegerTagData(FuelKey.FUEL_MAX.getKey());
	}

	/**
	 * Updates the current fuel level on the given ItemStack's NBT.
	 *
	 * @param item the fuel item to update
	 * @param current the new fuel level (clamped to [0, max])
	 *
	 * @return the updated ItemStack
	 */
	public static ItemStack setCurrentFuel(ItemStack item, int current) {
		int max     = getMaxFuel(item);
		int clamped = Math.max(0, Math.min(current, max));

		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), clamped);
		return builder.build();
	}

	/**
	 * Updates the max fuel capacity on the given ItemStack's NBT. Also clamps current fuel if it would exceed the new
	 * max. Works on any item that has fuel capacity (not limited to registered fuel items).
	 *
	 * @param item the item to update
	 * @param max the new maximum fuel capacity (clamped to &ge; 0)
	 *
	 * @return the updated ItemStack
	 */
	public static ItemStack setMaxFuel(ItemStack item, int max) {
		if (!hasFuelCapacity(item)) return item;

		int newMax  = Math.max(0, max);
		int current = readFuelCurrent(item);
		int clamped = Math.min(current, newMax);

		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), newMax);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), clamped);
		return builder.build();
	}

	// =========================================================================
	// Raw NBT helpers (work on any item with fuel tags, no FUEL_ID required)
	// =========================================================================

	/**
	 * Returns whether the given item has fuel capacity — i.e. carries a {@link FuelKey#FUEL_CURRENT} NBT tag. Unlike
	 * {@link #isFuelItem}, this does not require the {@link FuelKey#FUEL_ID} tag and therefore also matches wearables
	 * such as jetpacks that store fuel directly on the item.
	 */
	public static boolean hasFuelCapacity(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		return new ItemBuilder(item).hasNBTTag(FuelKey.FUEL_CURRENT.getKey());
	}

	/**
	 * Reads {@link FuelKey#FUEL_CURRENT} from NBT without requiring a {@link FuelKey#FUEL_ID} tag.
	 *
	 * @return the raw tag value, or 0 if absent
	 */
	public static int readFuelCurrent(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return 0;
		return new ItemBuilder(item).getIntegerTagData(FuelKey.FUEL_CURRENT.getKey());
	}

	/**
	 * Reads {@link FuelKey#FUEL_MAX} from NBT without requiring a {@link FuelKey#FUEL_ID} tag.
	 *
	 * @return the raw tag value, or 0 if absent
	 */
	public static int readFuelMax(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return 0;
		return new ItemBuilder(item).getIntegerTagData(FuelKey.FUEL_MAX.getKey());
	}

	/**
	 * Writes {@link FuelKey#FUEL_CURRENT} to NBT, clamping to {@code [0, max]}. Does not require a
	 * {@link FuelKey#FUEL_ID} tag — safe to use on wearables with embedded fuel.
	 *
	 * @param item the item to update
	 * @param current the desired current fuel level
	 *
	 * @return the updated ItemStack
	 */
	public static ItemStack writeFuelCurrent(ItemStack item, int current) {
		int max     = readFuelMax(item);
		int clamped = max > 0 ? Math.max(0, Math.min(current, max)) : Math.max(0, current);

		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), clamped);
		return builder.build();
	}

	/**
	 * Stamps all fuel NBT tags onto an ItemStack. Called when building a new fuel unique item.
	 *
	 * @param builder the ItemBuilder to stamp onto
	 */
	public void stampNBT(ItemBuilder builder) {
		builder.addTag(FuelKey.FUEL_ID.getKey(), fuelKey);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), maxFuel);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), maxFuel);
	}

}
