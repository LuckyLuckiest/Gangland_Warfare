package org.luckyraven.gangland.item.refresher;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.item.ItemRefresher;

/**
 * Rebuilds a car ItemStack into a factory-fresh copy: {@link Car#buildItem(Player)} re-stamps full fuel, full
 * durability, and the configured custom-model-data, stripping whatever frozen state the admin's template carried
 * (partial fuel, used durability, a prior owner UUID). Car ownership is stamped separately when the buyer first mounts
 * (see {@code VehicleSession.buildReturnItem}), so this refresher deliberately does not touch {@code car_owner}.
 */
@RequiredArgsConstructor
public class CarItemRefresher implements ItemRefresher {

	private final CarAddon carAddon;

	@Override
	public boolean canRefresh(ItemStack source) {
		return Car.isCarItem(source);
	}

	@Override
	@Nullable
	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		String carId = Car.getCarId(source);
		if (carId == null || carId.isEmpty()) return null;

		Car car = carAddon.getCar(carId);
		if (car == null) return null;

		ItemStack built = context != null ? car.buildItem(context) : car.buildItem();
		if (built == null) return null;

		built.setAmount(source.getAmount());
		return built;
	}

}
