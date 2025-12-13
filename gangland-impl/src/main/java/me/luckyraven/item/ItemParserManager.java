package me.luckyraven.item;

import lombok.Getter;
import me.luckyraven.item.converter.AmmunitionConverter;
import me.luckyraven.item.converter.MaterialConverter;
import me.luckyraven.item.converter.WeaponConverter;
import me.luckyraven.util.item.ItemConverterRegistry;
import me.luckyraven.util.item.ItemParser;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

@Getter
public class ItemParserManager {

	private final ItemConverterRegistry registry;
	private final ItemParser            parser;

	public ItemParserManager(WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		this.registry = new ItemConverterRegistry();
		this.parser   = new ItemParser(registry);

		registry.register("material", new MaterialConverter());
		registry.register("weapon", new WeaponConverter(weaponService));

		var ammunitionConverter = new AmmunitionConverter(ammunitionAddon);

		registry.register("ammunition", ammunitionConverter);
		registry.register("ammo", ammunitionConverter);
	}
}
