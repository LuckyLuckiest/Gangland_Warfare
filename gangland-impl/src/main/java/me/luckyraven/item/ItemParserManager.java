package me.luckyraven.item;

import lombok.Getter;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.item.converter.*;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.wearable.WearableService;

@Getter
public class ItemParserManager {

	private final ItemConverterRegistry registry;
	private final ItemParser            parser;

	public ItemParserManager(WeaponService weaponService, AmmunitionManager ammunitionManager,
	                         WearableService wearableService, CarManager carManager) {
		this.registry = new ItemConverterRegistry();
		this.parser   = new ItemParser(registry);

		registry.register("material", new MaterialConverter());
		registry.register("weapon", new WeaponConverter(weaponService));
		registry.register("wearable", new WearableConverter(wearableService));
		registry.register("car", new CarConverter(carManager));

		var ammunitionConverter = new AmmunitionConverter(ammunitionManager);

		registry.register("ammunition", ammunitionConverter);
		registry.register("ammo", ammunitionConverter);
	}
}
