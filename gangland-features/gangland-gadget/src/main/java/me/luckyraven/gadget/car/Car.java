package me.luckyraven.gadget.car;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.item.fuel.FuelKey;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
	private final String       permission;

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
	 * Builds an ItemStack representing this car in inventory form, stamped with identifying NBT tags.
	 */
	public ItemStack buildItem() {
		ItemBuilder builder = new ItemBuilder(itemMaterial);
		builder.setDisplayName(displayName);

		if (lore != null && !lore.isEmpty()) {
			builder.setLore(lore);
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
}
