package org.luckyraven.gangland.weapon;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.compatibility.CompatibilityWorker;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.ItemConverterRegistry;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemPredicates;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.item.ItemSerializerRegistry;
import org.luckyraven.gangland.item.NbtTagCatalog;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.contract.WearableEquipService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.command.DebugWeaponContribution;
import org.luckyraven.gangland.weapon.command.ItemWearableContribution;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.data.WeaponDataCleanupTask;
import org.luckyraven.gangland.weapon.death.WeaponDeathMessageContributor;
import org.luckyraven.gangland.weapon.file.WeaponBlockRegenerationSettings;
import org.luckyraven.gangland.weapon.fire.PluginFireRegistry;
import org.luckyraven.gangland.weapon.item.AmmunitionConverter;
import org.luckyraven.gangland.weapon.item.AmmunitionItemRefresher;
import org.luckyraven.gangland.weapon.item.AmmunitionItemSerializer;
import org.luckyraven.gangland.weapon.item.WeaponConverter;
import org.luckyraven.gangland.weapon.item.WeaponItemPredicates;
import org.luckyraven.gangland.weapon.item.WeaponItemSerializer;
import org.luckyraven.gangland.weapon.item.WeaponRefresher;
import org.luckyraven.gangland.weapon.item.WearableConverter;
import org.luckyraven.gangland.weapon.item.WearableItemSerializer;
import org.luckyraven.gangland.weapon.item.WearableRefresher;
import org.luckyraven.gangland.weapon.metrics.WeaponMetricsContributor;
import org.luckyraven.gangland.weapon.modifiers.BlockDamageManager;
import org.luckyraven.gangland.weapon.raytrace.WeaponRaytracer;
import org.luckyraven.gangland.weapon.raytrace.WeaponVisualSpawner;
import org.luckyraven.gangland.weapon.shop.WeaponShopDisplayNameProvider;
import org.luckyraven.gangland.weapon.sign.WeaponSignContribution;
import org.luckyraven.gangland.weapon.sign.view.WeaponSignViewProvider;
import org.luckyraven.gangland.weapon.wearable.WearableAddon;
import org.luckyraven.gangland.weapon.wearable.WearableService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.repository.IRepository;

/**
 * CONFIG-phase wiring for the weapon module: the weapon/wearable managers, item framework registrations, and every
 * seam bean (metrics, cleanup, NBT catalogue, shop display, death message, sign types/view, command contributions)
 * this module contributes to the core.
 */
@Configuration
public final class WeaponModuleConfig {

	private final Gangland           gangland;
	private final DependencyContainer container;

	public WeaponModuleConfig(Gangland gangland, DependencyContainer container) {
		this.gangland  = gangland;
		this.container = container;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Weapon system
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public WeaponManager weaponManager(WeaponAddon weaponAddon, GanglandDatabase database) {
		return new WeaponManager(weaponAddon, database);
	}

	@Bean
	public WeaponService weaponService(WeaponManager weaponManager) {
		return weaponManager;
	}

	@Bean
	public BlockDamageManager blockDamageManager(WeaponBlockRegenerationSettings settings) {
		return new BlockDamageManager(gangland, settings);
	}

	@Bean
	public WeaponVisualSpawner weaponVisualSpawner() {
		return new WeaponVisualSpawner();
	}

	@Bean
	public WeaponRaytracer weaponRaytracer(WeaponManager weaponManager, WearableAddon wearableAddon,
	                                       BlockDamageManager blockDamageManager,
	                                       WeaponVisualSpawner weaponVisualSpawner) {
		WeaponRaytracer raytracer = new WeaponRaytracer(weaponManager, wearableAddon, blockDamageManager,
		                                                weaponVisualSpawner);
		// Cross-module raytracer publishing — same as the legacy events() method did.
		Bukkit.getServicesManager().register(WeaponRaytracer.class, raytracer, gangland, ServicePriority.Normal);
		return raytracer;
	}

	@Bean
	public WearableService wearableService(WearableAddon wearableAddon) {
		return wearableAddon;
	}

	@Bean
	public WearableEquipService wearableEquipService(WearableAddon wearableAddon) {
		return wearableAddon;
	}

	@Bean
	public RecoilCompatibility recoilCompatibility(CompatibilityWorker compatibilityWorker) {
		return compatibilityWorker.getRecoilCompatibility();
	}

	@Bean
	public PluginFireRegistry pluginFireRegistry() {
		return new PluginFireRegistry();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Item framework — converters, serializers, refreshers
	// ---------------------------------------------------------------------------------------------------------------

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
	public WeaponRefresher weaponRefresher(WeaponService weaponService) {
		return new WeaponRefresher(weaponService);
	}

	@Bean
	public WearableRefresher wearableRefresher(WearableService wearableService) {
		return new WearableRefresher(wearableService);
	}

	@Bean
	public AmmunitionItemRefresher ammunitionItemRefresher(AmmunitionManager ammunitionManager) {
		return new AmmunitionItemRefresher(ammunitionManager);
	}

	/**
	 * Registers every weapon/wearable/ammunition converter, serializer and refresher into the core's existing
	 * {@link ItemConverterRegistry} / {@link ItemSerializerRegistry} / {@link ItemRefresherRegistry} beans — the
	 * registry parameters are the ordering edge (the core registry bean is always built first). Serializers use
	 * the plain default-priority overload (§1.6(a): the only precedence that matters, UNIQUE ahead of WEAPON, is
	 * already preserved because the core's registry bean runs first and the sort is stable). Refreshers register
	 * {@code weaponRefresher}/{@code wearableRefresher} at priority {@code 10} — ahead of the core's
	 * {@code uniqueItemRefresher} — and {@code ammunitionItemRefresher} at the default priority {@code 0}, behind
	 * it, reproducing today's exact insertion order (weapon, wearable, unique, ammunition).
	 */
	@Bean
	public WeaponItemRegistrations weaponItemRegistrations(ItemConverterRegistry itemConverterRegistry,
	                                                        ItemSerializerRegistry itemSerializerRegistry,
	                                                        ItemRefresherRegistry itemRefresherRegistry,
	                                                        WeaponConverter weaponConverter,
	                                                        AmmunitionConverter ammunitionConverter,
	                                                        WearableConverter wearableConverter,
	                                                        WeaponItemSerializer weaponItemSerializer,
	                                                        AmmunitionItemSerializer ammunitionItemSerializer,
	                                                        WearableItemSerializer wearableItemSerializer,
	                                                        WeaponRefresher weaponRefresher,
	                                                        WearableRefresher wearableRefresher,
	                                                        AmmunitionItemRefresher ammunitionItemRefresher) {
		itemConverterRegistry.register(ItemKind.WEAPON, weaponConverter);
		itemConverterRegistry.register(ItemKind.AMMUNITION, ammunitionConverter);
		itemConverterRegistry.register("ammo", ammunitionConverter);
		itemConverterRegistry.register(ItemKind.WEARABLE, wearableConverter);

		itemSerializerRegistry.register(WeaponItemPredicates.WEAPON, weaponItemSerializer);
		itemSerializerRegistry.register(WeaponItemPredicates.AMMUNITION, ammunitionItemSerializer);
		itemSerializerRegistry.register(ItemPredicates.WEARABLE, wearableItemSerializer);

		itemRefresherRegistry.register(weaponRefresher, 10);
		itemRefresherRegistry.register(wearableRefresher, 10);
		itemRefresherRegistry.register(ammunitionItemRefresher);

		return new WeaponItemRegistrations();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class WeaponItemRegistrations {
	}

	// ---------------------------------------------------------------------------------------------------------------
	// NBT tag catalogue
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public WeaponNbtTags weaponNbtTags(NbtTagCatalog nbtTagCatalog) {
		for (WeaponTag tag : WeaponTag.values()) {
			nbtTagCatalog.register(tag.name().toLowerCase());
		}
		return new WeaponNbtTags();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class WeaponNbtTags {
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sign, shop, cleanup, metrics and death-message seams
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public WeaponSignContribution weaponSignContribution(WeaponService weaponService,
	                                                     AmmunitionManager ammunitionManager,
	                                                     WearableService wearableService,
	                                                     @Qualifier("online") UserManager<Player> userManager) {
		return new WeaponSignContribution(weaponService, ammunitionManager, wearableService, userManager);
	}

	@Bean
	public WeaponSignViewProvider weaponSignViewProvider(Gangland gangland, WeaponService weaponService,
	                                                     AmmunitionManager ammunitionManager,
	                                                     WearableService wearableService,
	                                                     UniqueItemAddon uniqueItemAddon) {
		return new WeaponSignViewProvider(gangland, weaponService, ammunitionManager, wearableService,
		                                  uniqueItemAddon);
	}

	@Bean
	public WeaponShopDisplayNameProvider weaponShopDisplayNameProvider(WeaponService weaponService) {
		return new WeaponShopDisplayNameProvider(weaponService);
	}

	@Bean
	public WeaponDataCleanupTask weaponDataCleanupTask(WeaponManager weaponManager, GanglandDatabase database) {
		IRepository<Weapon> weaponRepository = database.getRepositoryRegistry().getRepository(Weapon.class);
		return new WeaponDataCleanupTask(weaponManager, weaponRepository);
	}

	@Bean
	public WeaponMetricsContributor weaponMetricsContributor(WeaponAddon weaponAddon) {
		return new WeaponMetricsContributor(weaponAddon);
	}

	@Bean
	public WeaponDeathMessageContributor weaponDeathMessageContributor(WeaponManager weaponManager) {
		return new WeaponDeathMessageContributor(weaponManager);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Command contributions
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public DebugWeaponContribution debugWeaponContribution(WeaponManager weaponManager) {
		return new DebugWeaponContribution(gangland, weaponManager);
	}

	@Bean
	public ItemWearableContribution itemWearableContribution(@Qualifier("online") UserManager<Player> userManager,
	                                                         WearableAddon wearableAddon) {
		return new ItemWearableContribution(gangland, userManager, wearableAddon);
	}
}
