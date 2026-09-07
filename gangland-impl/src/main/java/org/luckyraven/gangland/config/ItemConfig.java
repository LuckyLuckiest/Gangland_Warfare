package org.luckyraven.gangland.config;

import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.gangland.item.*;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.converter.*;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyConverter;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.refresher.*;
import org.luckyraven.gangland.item.serializer.*;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;

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
	                                                   UniqueConverter uniqueConverter,
	                                                   MoneyConverter moneyConverter) {
		ItemConverterRegistry registry = new ItemConverterRegistry();
		registry.register(ItemKind.MATERIAL, materialConverter);
		registry.register(ItemKind.WEAPON, weaponConverter);

		registry.register(ItemKind.AMMUNITION, ammunitionConverter);
		registry.register("ammo", ammunitionConverter);

		registry.register(ItemKind.WEARABLE, wearableConverter);
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
	public MoneyItemSerializer moneyItemSerializer() {
		return new MoneyItemSerializer();
	}

	@Bean
	public ItemSerializerRegistry itemSerializerRegistry(UniqueItemSerializer uniqueItemSerializer,
	                                                     WeaponItemSerializer weaponItemSerializer,
	                                                     AmmunitionItemSerializer ammunitionItemSerializer,
	                                                     WearableItemSerializer wearableItemSerializer,
	                                                     MoneyItemSerializer moneyItemSerializer,
	                                                     MaterialItemSerializer materialItemSerializer) {
		ItemSerializerRegistry registry = new ItemSerializerRegistry();
		// Registration order is priority order: the first predicate that matches wins. Unique goes first because
		// a unique item can wrap any underlying domain (a unique weapon stamps *both* the weapon tag and the
		// uniqueItem tag, and we want it identified as unique). Material is the final catch-all — it registers at
		// CATCH_ALL_PRIORITY (not the default priority) so it always sorts last even after a runtime module
		// registers its own serializer later, at the default priority, in its own CONFIG phase.
		registry.register(ItemPredicates.UNIQUE, uniqueItemSerializer);
		registry.register(ItemPredicates.WEAPON, weaponItemSerializer);
		registry.register(ItemPredicates.AMMUNITION, ammunitionItemSerializer);
		registry.register(ItemPredicates.WEARABLE, wearableItemSerializer);
		registry.register(ItemPredicates.MONEY, moneyItemSerializer);
		registry.register(ItemPredicates.MATERIAL, materialItemSerializer, ItemSerializerRegistry.CATCH_ALL_PRIORITY);
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
	public AmmunitionItemRefresher ammunitionItemRefresher(AmmunitionManager ammunitionManager) {
		return new AmmunitionItemRefresher(ammunitionManager);
	}

	@Bean
	public ItemRefresherRegistry itemRefresherRegistry(WeaponRefresher weaponRefresher,
	                                                   WearableRefresher wearableRefresher,
	                                                   UniqueItemRefresher uniqueItemRefresher,
	                                                   AmmunitionItemRefresher ammunitionItemRefresher) {
		ItemRefresherRegistry registry = new ItemRefresherRegistry();
		registry.register(weaponRefresher, wearableRefresher, uniqueItemRefresher, ammunitionItemRefresher);
		return registry;
	}
}
