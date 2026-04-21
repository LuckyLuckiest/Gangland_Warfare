package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.PostConstruct;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.GanglandMoneyDepositService;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.inventory.InventoryDefinitionStore;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.inventory.InventoryRuntimeContext;
import me.luckyraven.file.configuration.inventory.itemsource.GangItemSourceProvider;
import me.luckyraven.file.configuration.lootchest.GanglandLootChestMessages;
import me.luckyraven.file.configuration.lootchest.LootChestSettings;
import me.luckyraven.file.configuration.weapon.GanglandBlockRegenerationSettings;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.hologram.HologramService;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.item.ItemConverterRegistry;
import me.luckyraven.item.ItemParser;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.contract.GanglandUniqueItemInteractionService;
import me.luckyraven.item.contract.UniqueItemInteractionService;
import me.luckyraven.item.contract.UniqueItemRegistry;
import me.luckyraven.item.contract.WearableEquipService;
import me.luckyraven.item.listener.money.MoneyProximityPickupTask;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.config.LootChestLoader;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.sign.SignManager;
import me.luckyraven.sign.bulk.BulkActionManager;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignFormatterService;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.WeaponAddon;
import me.luckyraven.weapon.modifiers.BlockDamageManager;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponVisualSpawner;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

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
	                                                       ItemParser itemParser) {
		ItemSourceProvider itemSourceProvider = new GangItemSourceProvider(userManager, gangManager);
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
		loader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));
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
	public SignInteraction signInteraction(SignTypeRegistry signTypeRegistry,
	                                       SignFormatterService signFormatterService,
	                                       SignInformation signInformation) {
		String prefix = Gangland.SHORT_PREFIX + "-";
		return new SignInteraction(prefix, signTypeRegistry, signFormatterService, signInformation);
	}

	@Bean
	public BulkActionManager bulkActionManager(SignInformation signInformation) {
		return new BulkActionManager(gangland, signInformation);
	}

	@Bean
	public SignManager signManager(SignTypeRegistry signTypeRegistry,
	                               SignInteraction signInteraction,
	                               WeaponManager weaponManager,
	                               AmmunitionManager ammunitionManager,
	                               UniqueItemAddon uniqueItemAddon,
	                               @Qualifier("online") UserManager<Player> userManager,
	                               @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                               WearableAddon wearableAddon,
	                               CarAddon carAddon) {
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
	                                               MoneyAddon moneyAddon,
	                                               PlaceholderService placeholderService) {
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
	public LootChestManager lootChestManager(HologramService hologramService,
	                                         RepositoryRegistry repositoryRegistry,
	                                         ItemParser itemParser) {
		return new LootChestManager(gangland, Gangland.FULL_PREFIX, hologramService, repositoryRegistry,
		                            itemParser, new GanglandLootChestMessages());
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
