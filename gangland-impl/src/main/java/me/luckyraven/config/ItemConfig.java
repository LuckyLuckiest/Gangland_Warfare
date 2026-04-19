package me.luckyraven.config;

import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.item.*;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.converter.*;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyConverter;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.item.refresher.UniqueItemRefresher;
import me.luckyraven.item.refresher.WeaponRefresher;
import me.luckyraven.item.refresher.WearableRefresher;
import me.luckyraven.item.serializer.*;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.wearable.WearableService;

/**
 * CONFIG-phase wiring for the item framework: every converter (string → ItemStack), serializer (ItemStack → string),
 * and refresher (live-item updater) lives here as its own bean. The three registries ({@link ItemConverterRegistry},
 * {@link ItemSerializerRegistry}, {@link ItemRefresherRegistry}) are composed from the component beans so individual
 * converters/serializers/refreshers can be overridden or swapped in tests without rewiring the registry.
 *
 * <p>Registrations are keyed by {@link ItemKind} (labels) and {@link ItemPredicates} (runtime checks). Both sides
 * share the same enum, so renaming a label flows to every converter and serializer automatically.
 */
@Configuration
public class ItemConfig {

	// ── converters ───────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public MaterialConverter materialConverter() {
		return new MaterialConverter();
	}

	@Bean
	public WeaponConverter weaponConverter(WeaponService weaponService) {
		return new WeaponConverter(weaponService);
	}

	@Bean
	public AmmunitionConverter ammunitionConverter(AmmunitionManager ammunitionManager) {
		return new AmmunitionConverter(ammunitionManager);
	}

	@Bean
	public WearableConverter wearableConverter(WearableService wearableService) {
		return new WearableConverter(wearableService);
	}

	@Bean
	public CarConverter carConverter(CarAddon carAddon) {
		return new CarConverter(carAddon);
	}

	@Bean
	public UniqueConverter uniqueConverter(UniqueItemAddon uniqueItemAddon) {
		return new UniqueConverter(uniqueItemAddon);
	}

	@Bean
	public MoneyConverter moneyConverter(MoneyAddon moneyAddon, MoneyDepositService moneyDepositService) {
		return new MoneyConverter(moneyAddon, moneyDepositService);
	}

	@Bean
	public ItemConverterRegistry itemConverterRegistry(MaterialConverter materialConverter,
	                                                   WeaponConverter weaponConverter,
	                                                   AmmunitionConverter ammunitionConverter,
	                                                   WearableConverter wearableConverter,
	                                                   CarConverter carConverter,
	                                                   UniqueConverter uniqueConverter,
	                                                   MoneyConverter moneyConverter) {
		ItemConverterRegistry registry = new ItemConverterRegistry();
		registry.register(ItemKind.MATERIAL, materialConverter);
		registry.register(ItemKind.WEAPON, weaponConverter);

		registry.register(ItemKind.AMMUNITION, ammunitionConverter);
		registry.register("ammo", ammunitionConverter);

		registry.register(ItemKind.WEARABLE, wearableConverter);
		registry.register(ItemKind.CAR, carConverter);
		registry.register(ItemKind.UNIQUE, uniqueConverter);

		registry.register(ItemKind.MONEY, moneyConverter);
		registry.register("cash", moneyConverter);
		return registry;
	}

	@Bean
	public ItemParser itemParser(ItemConverterRegistry itemConverterRegistry) {
		return new ItemParser(itemConverterRegistry);
	}

	// ── serializers ──────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public MaterialItemSerializer materialItemSerializer() {
		return new MaterialItemSerializer();
	}

	@Bean
	public UniqueItemSerializer uniqueItemSerializer() {
		return new UniqueItemSerializer();
	}

	@Bean
	public WeaponItemSerializer weaponItemSerializer() {
		return new WeaponItemSerializer();
	}

	@Bean
	public AmmunitionItemSerializer ammunitionItemSerializer() {
		return new AmmunitionItemSerializer();
	}

	@Bean
	public WearableItemSerializer wearableItemSerializer() {
		return new WearableItemSerializer();
	}

	@Bean
	public CarItemSerializer carItemSerializer() {
		return new CarItemSerializer();
	}

	@Bean
	public MoneyItemSerializer moneyItemSerializer() {
		return new MoneyItemSerializer();
	}

	@Bean
	public ItemSerializerRegistry itemSerializerRegistry(UniqueItemSerializer uniqueItemSerializer,
	                                                     WeaponItemSerializer weaponItemSerializer,
	                                                     AmmunitionItemSerializer ammunitionItemSerializer,
	                                                     WearableItemSerializer wearableItemSerializer,
	                                                     CarItemSerializer carItemSerializer,
	                                                     MoneyItemSerializer moneyItemSerializer,
	                                                     MaterialItemSerializer materialItemSerializer) {
		ItemSerializerRegistry registry = new ItemSerializerRegistry();
		// Registration order is priority order: the first predicate that matches wins. Unique goes first because
		// a unique item can wrap any underlying domain (a unique weapon stamps *both* the weapon tag and the
		// uniqueItem tag, and we want it identified as unique). Material is the final catch-all.
		registry.register(ItemPredicates.UNIQUE, uniqueItemSerializer);
		registry.register(ItemPredicates.WEAPON, weaponItemSerializer);
		registry.register(ItemPredicates.AMMUNITION, ammunitionItemSerializer);
		registry.register(ItemPredicates.WEARABLE, wearableItemSerializer);
		registry.register(ItemPredicates.CAR, carItemSerializer);
		registry.register(ItemPredicates.MONEY, moneyItemSerializer);
		registry.register(ItemPredicates.MATERIAL, materialItemSerializer);
		return registry;
	}

	// ── refreshers ───────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public WeaponRefresher weaponRefresher(WeaponService weaponService) {
		return new WeaponRefresher(weaponService);
	}

	@Bean
	public WearableRefresher wearableRefresher(WearableService wearableService) {
		return new WearableRefresher(wearableService);
	}

	@Bean
	public UniqueItemRefresher uniqueItemRefresher(UniqueItemAddon uniqueItemAddon) {
		return new UniqueItemRefresher(uniqueItemAddon);
	}

	@Bean
	public ItemRefresherRegistry itemRefresherRegistry(WeaponRefresher weaponRefresher,
	                                                   WearableRefresher wearableRefresher,
	                                                   UniqueItemRefresher uniqueItemRefresher) {
		ItemRefresherRegistry registry = new ItemRefresherRegistry();
		registry.register(weaponRefresher, wearableRefresher, uniqueItemRefresher);
		return registry;
	}
}
