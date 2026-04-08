package me.luckyraven.item;

import lombok.Getter;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.converter.*;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyConverter;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.wearable.WearableService;

@Getter
public class ItemParserManager {

	private final ItemConverterRegistry registry;
	private final ItemParser            parser;

	public ItemParserManager(WeaponService weaponService, AmmunitionManager ammunitionManager,
	                         WearableService wearableService, CarManager carManager, MoneyAddon moneyAddon,
	                         MoneyDepositService moneyDepositService, UniqueItemAddon uniqueItemAddon) {
		this.registry = new ItemConverterRegistry();
		this.parser   = new ItemParser(registry);

		registry.register("material", new MaterialConverter());
		registry.register("weapon", new WeaponConverter(weaponService));
		registry.register(new String[]{"ammunition", "ammo"}, new AmmunitionConverter(ammunitionManager));
		registry.register("wearable", new WearableConverter(wearableService));
		registry.register("car", new CarConverter(carManager));
		registry.register("unique", new UniqueConverter(uniqueItemAddon));
		registry.register(new String[]{"money", "cash"}, new MoneyConverter(moneyAddon, moneyDepositService));
	}
}
