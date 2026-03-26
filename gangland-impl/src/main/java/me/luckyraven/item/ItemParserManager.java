package me.luckyraven.item;

import lombok.Getter;
import me.luckyraven.item.converter.AmmunitionConverter;
import me.luckyraven.item.converter.MaterialConverter;
import me.luckyraven.item.converter.WeaponConverter;
import me.luckyraven.item.converter.WearableConverter;
import me.luckyraven.util.item.ItemConverterRegistry;
import me.luckyraven.util.item.ItemParser;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.wearable.WearableService;

@Getter
public class ItemParserManager {

	private final ItemConverterRegistry registry;
	private final ItemParser            parser;

	public ItemParserManager(WeaponService weaponService, AmmunitionManager ammunitionManager,
							 WearableService wearableService) {
		this.registry = new ItemConverterRegistry();
		this.parser   = new ItemParser(registry);

		registry.register("material", new MaterialConverter());
		registry.register("weapon", new WeaponConverter(weaponService));
		registry.register("wearable", new WearableConverter(wearableService));

		var ammunitionConverter = new AmmunitionConverter(ammunitionManager);

		registry.register("ammunition", ammunitionConverter);
		registry.register("ammo", ammunitionConverter);
	}
}
