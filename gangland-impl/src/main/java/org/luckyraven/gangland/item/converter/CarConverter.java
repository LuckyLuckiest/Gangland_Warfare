package org.luckyraven.gangland.item.converter;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.item.ItemAttributes;

import java.util.Map;

/**
 * Converts a {@code car:<name>} item string into a fully-built {@link Car} ItemStack.
 *
 * <p>Usage examples:
 * <pre>
 *   car:sports_car
 *   car:pickup_truck{name=Custom Name}
 * </pre>
 *
 * <p>The {@code modifier} is the car registry key (the name defined in {@code cars.yml}). Attributes supported by
 * {@link ItemAttributes#applyAttributes} (name, lore, color) are applied after the item is built.
 */
@RequiredArgsConstructor
public class CarConverter extends ItemAttributes {

	private final CarManager carManager;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Car car = carManager.getCar(modifier.trim());

		if (car == null) {
			return null;
		}

		ItemStack itemStack = car.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}

}
