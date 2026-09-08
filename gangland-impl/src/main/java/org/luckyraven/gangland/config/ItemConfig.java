package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.item.MaterialItemSerializer;
import org.luckyraven.keystone.item.spi.ItemVocabularies;
import org.luckyraven.keystone.item.spi.ItemVocabulary;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemPredicates;
import org.luckyraven.gangland.item.NbtTagCatalog;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.converter.*;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyConverter;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.refresher.*;
import org.luckyraven.gangland.item.serializer.*;
import org.luckyraven.gangland.lootchest.LootChestWandTag;

import java.util.List;

/**
 * CONFIG-phase wiring for the item framework: every converter (string → ItemStack), serializer (ItemStack → string),
 * and refresher (live-item updater) lives here as its own bean. The three registries ({@link ItemConverterRegistry},
 * {@link ItemSerializerRegistry}, {@link ItemRefresherRegistry}) are composed from the component beans so individual
 * converters/serializers/refreshers can be overridden or swapped in tests without rewiring the registry.
 *
 * <p>Registrations are keyed by {@link ItemKind} (labels) and {@link ItemPredicates} (runtime checks). Both sides
 * share the same enum, so renaming a label flows to every converter and serializer automatically.
 */
@CustomLog
@Configuration
public class ItemConfig {

	private final GanglandContext context;

	public ItemConfig(GanglandContext context) {
		this.context = context;
	}

	// ── converters ───────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public MaterialConverter materialConverter() {
		return new MaterialConverter();
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
	                                                   UniqueConverter uniqueConverter,
	                                                   MoneyConverter moneyConverter) {
		ItemConverterRegistry registry = new ItemConverterRegistry();
		registry.register(ItemKind.MATERIAL, materialConverter);

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
	public MoneyItemSerializer moneyItemSerializer() {
		return new MoneyItemSerializer();
	}

	/**
	 * Registration order is priority order: the first predicate that matches wins. Unique goes first because a
	 * unique item can wrap any underlying domain (a unique weapon stamps *both* the weapon tag and the uniqueItem
	 * tag, and we want it identified as unique — preserved because this bean, and therefore this registry instance,
	 * is always built before any module's CONFIG-phase beans append to it). Material is the final catch-all — it
	 * registers at {@link ItemSerializerRegistry#CATCH_ALL_PRIORITY} (not the default priority) so it always sorts
	 * last even after a runtime module registers its own serializer later, at the default priority, in its own
	 * CONFIG phase.
	 */
	@Bean
	public ItemSerializerRegistry itemSerializerRegistry(UniqueItemSerializer uniqueItemSerializer,
	                                                     MoneyItemSerializer moneyItemSerializer,
	                                                     MaterialItemSerializer materialItemSerializer) {
		ItemSerializerRegistry registry = new ItemSerializerRegistry();
		registry.register(ItemPredicates.UNIQUE, uniqueItemSerializer);
		registry.register(ItemPredicates.MONEY, moneyItemSerializer);
		registry.register(ItemPredicates.MATERIAL, materialItemSerializer, ItemSerializerRegistry.CATCH_ALL_PRIORITY);
		return registry;
	}

	// ── refreshers ───────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public UniqueItemRefresher uniqueItemRefresher(UniqueItemAddon uniqueItemAddon) {
		return new UniqueItemRefresher(uniqueItemAddon);
	}

	/**
	 * The weapon module registers {@code weaponRefresher}/{@code wearableRefresher} into this same registry via the
	 * priority overload at priority {@code 10} — ahead of {@code uniqueItemRefresher} here, because a unique weapon
	 * carries both the weapon and the uniqueItem NBT tag and must keep being rebuilt as a weapon — while
	 * {@code ammunitionItemRefresher} registers at the default priority, behind {@code uniqueItemRefresher}, so a
	 * unique ammunition stack still rebuilds as a unique item. That reproduces the pre-flip insertion order
	 * (weapon, wearable, unique, ammunition) exactly. See {@code ItemRefresherRegistry}'s javadoc.
	 */
	@Bean
	public ItemRefresherRegistry itemRefresherRegistry(UniqueItemRefresher uniqueItemRefresher) {
		ItemRefresherRegistry registry = new ItemRefresherRegistry();
		registry.register(uniqueItemRefresher);
		return registry;
	}

	// ── nbt tag catalogue ───────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public NbtTagCatalog nbtTagCatalog() {
		NbtTagCatalog catalog = new NbtTagCatalog();
		for (LootChestWandTag tag : LootChestWandTag.values()) {
			catalog.register(tag.toString().toLowerCase());
		}
		return catalog;
	}

	// ── published vocabularies ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * Bartizan (and any other plugin) publishes an {@link ItemVocabulary} on the ServicesManager; the core
	 * folds them into its own registries. This runs as a @PostConstruct rather than a @Bean because
	 * ItemVocabularies is a static utility with no instance to register, and because BeanFactory runs
	 * @PostConstruct after every CONFIG bean exists — a stronger ordering guarantee than parameter edges.
	 */
	@PostConstruct
	public void installItemVocabularies() {
		List<ItemVocabulary> vocabularies = Bukkit.getServicesManager()
				.getRegistrations(ItemVocabulary.class).stream()
				.map(RegisteredServiceProvider::getProvider).toList();
		ItemVocabularies.install(vocabularies,
		                         context.get(ItemConverterRegistry.class),
		                         context.get(ItemSerializerRegistry.class),
		                         context.get(ItemRefresherRegistry.class));
		log.info(vocabularies.isEmpty()
				? "Item vocabularies installed: none — weapon:/ammo:/wearable: item strings will not resolve"
				: "Item vocabularies installed: " + vocabularies.stream().map(ItemVocabulary::namespace).toList());
	}
}
