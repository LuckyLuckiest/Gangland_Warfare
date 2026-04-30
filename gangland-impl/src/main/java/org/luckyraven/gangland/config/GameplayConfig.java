package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.compatibility.CompatibilityWorker;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.PostConstruct;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.data.economy.GanglandMoneyDepositService;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.file.configuration.inventory.InventoryLoader;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.file.configuration.inventory.itemsource.GangItemSourceProvider;
import org.luckyraven.gangland.file.configuration.lootchest.GanglandLootChestMessages;
import org.luckyraven.gangland.file.configuration.lootchest.LootChestSettings;
import org.luckyraven.gangland.file.configuration.weapon.GanglandBlockRegenerationSettings;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.GangFilterAdapter;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.hologram.HologramService;
import org.luckyraven.gangland.inventory.condition.BooleanExpressionEvaluator;
import org.luckyraven.gangland.inventory.filter.*;
import org.luckyraven.gangland.inventory.multi.ItemSourceProvider;
import org.luckyraven.gangland.item.ItemConverterRegistry;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.contract.GanglandUniqueItemInteractionService;
import org.luckyraven.gangland.item.contract.UniqueItemInteractionService;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.contract.WearableEquipService;
import org.luckyraven.gangland.item.listener.money.MoneyProximityPickupTask;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.config.LootChestLoader;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.sign.SignManager;
import org.luckyraven.gangland.sign.bulk.BulkActionManager;
import org.luckyraven.gangland.sign.registry.SignFormatRegistry;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignFormatterService;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteraction;
import org.luckyraven.gangland.sign.service.SignInteractionService;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.fire.PluginFireRegistry;
import org.luckyraven.gangland.weapon.modifiers.BlockDamageManager;
import org.luckyraven.gangland.weapon.raytrace.WeaponRaytracer;
import org.luckyraven.gangland.weapon.raytrace.WeaponVisualSpawner;
import org.luckyraven.gangland.weapon.wearable.WearableService;

/**
 * CONFIG-phase wiring for the gameplay-side managers: weapons, signs, items, inventory, hologram, loot chest, money.
 * Every bean here can constructor-inject any FILE-phase or DATABASE-phase bean by type.
 *
 * <p>The structural ordering inside the topo sort:
 * <ol>
 *     <li>Weapon system: {@link WeaponManager} → {@link BlockDamageManager} / {@link WeaponVisualSpawner} →
 *     {@link WeaponRaytracer} (depends on the previous three).</li>
 *     <li>Sign system: registries → {@link SignFormatterService} → {@link SignInteraction} → {@link SignManager}
 *     and {@link BulkActionManager}.</li>
 *     <li>Money + items: {@link MoneyDepositService} (must precede the parser; {@code MoneyConverter} resolves the
 *     currency symbol via the contract on instantiation), then the converter beans → {@link ItemConverterRegistry}
 *     → {@link ItemParser}, which the loot chest + cops-n-crooks beans transitively consume.</li>
 *     <li>Loot chest: {@link HologramService} → {@link LootChestManager} → {@link LootChestLoader}.</li>
 * </ol>
 *
 * <p>Tiny bridge / contract beans live here too because they're trivial wrappers over the managers they bind to.
 */
@CustomLog
@Configuration
public class GameplayConfig {

	private final Gangland        gangland;
	private final GanglandContext context;

	public GameplayConfig(Gangland gangland, GanglandContext context) {
		this.gangland = gangland;
		this.context  = context;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Inventory runtime + loader
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Domain-agnostic filter plumbing — the registry tracks per-view {@link FilterBinding} specs, the store holds
	 * per-(binding, player) {@link SearchFilter} state, and the applier is the shared filter/sort pipeline that
	 * replaced the old hand-rolled gang-search code.
	 */
	@Bean
	public FilterRegistry filterRegistry() {
		return new FilterRegistry();
	}

	@Bean
	public FilterStore filterStore(FilterRegistry filterRegistry) {
		return new FilterStore(filterRegistry);
	}

	@Bean
	public FilterApplier filterApplier() {
		return new FilterApplier();
	}

	@Bean
	public SearchButtonFactory searchButtonFactory(FilterStore filterStore, FilterRegistry filterRegistry) {
		return new SearchButtonFactory(filterStore, filterRegistry);
	}

	/**
	 * Bridges FILE-phase {@link InventoryDefinitionStore} (pure data maps) and the CONFIG-phase services that
	 * registration + open-inventory logic needs (user manager, item source provider, condition evaluator, …). Owns the
	 * {@code registerInventory} and {@code openInventoryForPlayer} methods that used to live as statics on
	 * {@code InventoryAddon}.
	 */
	@Bean
	public InventoryRuntimeContext inventoryRuntimeContext(InventoryDefinitionStore definitionStore,
	                                                       BooleanExpressionEvaluator conditionEvaluator,
	                                                       PlaceholderService placeholderService,
	                                                       PermissionManager permissionManager,
	                                                       @Qualifier("online") UserManager<Player> userManager,
	                                                       GangManager gangManager,
	                                                       FilterStore filterStore,
	                                                       FilterApplier filterApplier,
	                                                       GangFilterAdapter gangFilterAdapter,
	                                                       MemberFilterAdapter memberFilterAdapter,
	                                                       ItemParser itemParser) {
		ItemSourceProvider itemSourceProvider = new GangItemSourceProvider(userManager, gangManager, filterStore,
		                                                                   filterApplier, gangFilterAdapter,
		                                                                   memberFilterAdapter);
		return new InventoryRuntimeContext(gangland, definitionStore, itemSourceProvider, conditionEvaluator,
		                                   userManager, permissionManager, placeholderService, itemParser);
	}

	/**
	 * {@link InventoryLoader} can't initialize during the FILE phase because its load callback parses prefixed item
	 * refs (weapon:awp, wearable:police_vest, …) via {@link ItemParser}, which is a CONFIG-phase bean. The
	 * {@link #initializeInventoryLoader()} {@code @PostConstruct} below runs the actual {@code initialize()} once every
	 * other CONFIG bean is built.
	 */
	@Bean
	public InventoryLoader inventoryLoader(FileManager fileManager, InventoryRuntimeContext inventoryRuntimeContext) {
		InventoryLoader loader = new InventoryLoader(gangland, fileManager, inventoryRuntimeContext);

		loader.addExpectedFile(new FileHandler(gangland, "alliance_stat", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "gang_stat", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_banking", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_bounty", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_gang_search", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "user_stat", "inventory", ".yml"));
		return loader;
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
	public BlockDamageManager blockDamageManager(GanglandBlockRegenerationSettings settings) {
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
	public RecoilCompatibility recoilCompatibility(CompatibilityWorker compatibilityWorker) {
		return compatibilityWorker.getRecoilCompatibility();
	}

	@Bean
	public PluginFireRegistry pluginFireRegistry() {
		return new PluginFireRegistry();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sign system
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public SignTypeRegistry signTypeRegistry() {
		return new SignTypeRegistry();
	}

	@Bean
	public SignFormatRegistry signFormatRegistry() {
		return new SignFormatRegistry();
	}

	@Bean
	public SignFormatterService signFormatterService(SignFormatRegistry signFormatRegistry) {
		return new SignFormatterService(signFormatRegistry);
	}

	@Bean
	public SignInteraction signInteraction(SignTypeRegistry signTypeRegistry, SignFormatterService signFormatterService,
	                                       SignInformation signInformation) {
		String prefix = Gangland.SHORT_PREFIX + "-";
		return new SignInteraction(prefix, signTypeRegistry, signFormatterService, signInformation);
	}

	@Bean
	public BulkActionManager bulkActionManager(SignInformation signInformation) {
		return new BulkActionManager(gangland, signInformation);
	}

	@Bean
	public SignManager signManager(SignTypeRegistry signTypeRegistry, SignInteraction signInteraction,
	                               WeaponManager weaponManager, AmmunitionManager ammunitionManager,
	                               UniqueItemAddon uniqueItemAddon,
	                               @Qualifier("online") UserManager<Player> userManager,
	                               @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                               WearableAddon wearableAddon, CarAddon carAddon) {
		SignManager manager = new SignManager(gangland, Gangland.SHORT_PREFIX, signTypeRegistry, signInteraction,
		                                      weaponManager, ammunitionManager, uniqueItemAddon, userManager,
		                                      offlineUserManager, wearableAddon, carAddon);
		manager.initialize();
		return manager;
	}

	@Bean
	public SignInteractionService signInteractionService(SignManager signManager) {
		return signManager.getSignService();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Money + item system
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public MoneyDepositService moneyDepositService(@Qualifier("online") UserManager<Player> userManager,
	                                               MoneyAddon moneyAddon, PlaceholderService placeholderService) {
		return new GanglandMoneyDepositService(userManager, moneyAddon, placeholderService);
	}

	@Bean
	public MoneyProximityPickupTask moneyProximityPickupTask(MoneyAddon moneyAddon,
	                                                         MoneyDepositService moneyDepositService) {
		MoneyProximityPickupTask task = new MoneyProximityPickupTask(moneyAddon, moneyDepositService);
		task.runTaskTimer(gangland, 10L, 10L);
		return task;
	}

	// Item framework (converters, serializers, refreshers, parsers + registries) lives in ItemConfig.

	@Bean
	public UniqueItemRegistry uniqueItemRegistry(UniqueItemAddon uniqueItemAddon) {
		return uniqueItemAddon;
	}

	@Bean
	public UniqueItemInteractionService uniqueItemInteractionService(InventoryRuntimeContext inventoryRuntimeContext) {
		return new GanglandUniqueItemInteractionService(inventoryRuntimeContext);
	}

	@Bean
	public WearableEquipService wearableEquipService(WearableAddon wearableAddon) {
		return wearableAddon;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Hologram + loot chest
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public HologramService hologramService() {
		return new HologramService(gangland);
	}

	@Bean
	public LootChestManager lootChestManager(HologramService hologramService, RepositoryRegistry repositoryRegistry,
	                                         ItemParser itemParser) {
		return new LootChestManager(gangland, Gangland.FULL_PREFIX, hologramService, repositoryRegistry, itemParser,
		                            new GanglandLootChestMessages());
	}

	@Bean
	public LootChestService lootChestService(LootChestManager lootChestManager) {
		return lootChestManager;
	}

	@Bean
	public LootChestLoader lootChestLoader(LootChestManager lootChestManager, FileManager fileManager) {
		LootChestLoader loader = new LootChestLoader(gangland, lootChestManager, new LootChestSettings(), false, null,
		                                             fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	/**
	 * {@link InventoryLoader} can't initialize during construction because its load callback parses prefixed item refs
	 * (weapon:awp, wearable:police_vest, …) via {@link ItemParser}, which is also a CONFIG-phase bean. By the time this
	 * {@code @PostConstruct} runs, every CONFIG bean is registered AND the per-bean hydrate hook has populated the
	 * {@code ItemConverterRegistry}, so the {@code SlotItemFactory} resolver lambda set up earlier in this config can
	 * dereference it.
	 */
	@PostConstruct
	public void initializeInventoryLoader() {
		InventoryLoader loader = context.get(InventoryLoader.class);
		if (loader != null) {
			loader.initialize();
		}
	}
}
