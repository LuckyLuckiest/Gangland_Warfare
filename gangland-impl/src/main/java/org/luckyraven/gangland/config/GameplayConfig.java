package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.gangland.data.economy.GanglandMoneyDepositService;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.file.configuration.inventory.InventoryLoader;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.file.configuration.inventory.itemsource.GangItemSourceProvider;
import org.luckyraven.gangland.file.configuration.lootchest.GanglandLootChestMessages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.lootchest.LootChestSettings;
import org.luckyraven.gangland.gang.GangFilterAdapter;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.hologram.HologramService;
import org.luckyraven.gangland.menu.condition.BooleanExpressionEvaluator;
import org.luckyraven.gangland.menu.filter.*;
import org.luckyraven.gangland.menu.multi.ItemSourceProvider;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.cooldown.InMemoryCooldownService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.contract.GanglandUniqueItemInteractionService;
import org.luckyraven.gangland.item.contract.UniqueItemInteractionService;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.listener.money.MoneyProximityPickupTask;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.config.LootChestLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.sign.LegacySignRewriter;
import org.luckyraven.gangland.sign.SignManager;
import org.luckyraven.gangland.sign.bulk.BulkActionManager;
import org.luckyraven.gangland.sign.registry.SignFormatRegistry;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignFormatterService;
import org.luckyraven.gangland.sign.SignPermissions;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteraction;
import org.luckyraven.gangland.sign.service.SignInteractionService;

/**
 * CONFIG-phase wiring for the gameplay-side managers: signs, items, inventory, hologram, loot chest, money. The
 * weapon/wearable system's own CONFIG-phase beans live in the weapon module's {@code WeaponModuleConfig}. Every
 * bean here can constructor-inject any FILE-phase or DATABASE-phase bean by type.
 *
 * <p>The structural ordering inside the topo sort:
 * <ol>
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
	 * WS2 G1 (0.10.0): the core's single {@code keystone-inventory} root. A plain {@code @Bean}, own instance —
	 * not resolved via the {@code ServicesManager}, since {@code keystone-inventory} is a {@code provided}-scope
	 * library dependency, not a second running plugin (contrast {@link org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder}'s
	 * {@code PlaceholderProvider} publication, which exists precisely because Plaque IS a separate plugin).
	 * {@code registerListeners} is called once here, at bean construction, per the module's own doc ("Keystone.jar
	 * registers no listener of its own"). Cooldowns are in-memory only — nothing in Gangland needs a
	 * database-backed cooldown yet; upgrade to {@code keystone-persistence}'s {@code PersistentCooldownService} if
	 * that changes. No menu is actually built through this service until WS2 G3 retargets
	 * {@link InventoryRuntimeContext} onto {@code ChestMenuBuilder} — until then this bean exists side by side with
	 * the old {@code inventory-api} framework, doing nothing yet.
	 */
	@Bean
	public InventoryService inventoryService() {
		InventoryService service = new InventoryService(new InMemoryCooldownService());
		service.registerListeners(gangland);
		log.info("keystone-inventory service registered");
		return service;
	}

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
	                                                       ItemParser itemParser,
	                                                       InventoryRegistry inventoryRegistry) {
		ItemSourceProvider itemSourceProvider = new GangItemSourceProvider(userManager, gangManager, filterStore,
		                                                                   filterApplier, gangFilterAdapter,
		                                                                   memberFilterAdapter);
		return new InventoryRuntimeContext(gangland, definitionStore, itemSourceProvider, conditionEvaluator,
		                                   userManager, permissionManager, placeholderService, itemParser,
		                                   inventoryRegistry);
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
	                                       SignInformation signInformation, PermissionManager permissionManager) {
		// LS-19: sign creation and breaking are permission-gated; surface both nodes the same way every other
		// gangland permission is discovered.
		permissionManager.addPermission(SignPermissions.CREATE);
		permissionManager.addPermission(SignPermissions.BREAK);

		String prefix = Gangland.SHORT_PREFIX + "-";
		return new SignInteraction(prefix, signTypeRegistry, signFormatterService, signInformation);
	}

	@Bean
	public BulkActionManager bulkActionManager(SignInformation signInformation) {
		return new BulkActionManager(gangland, signInformation);
	}

	@Bean
	public LegacySignRewriter legacySignRewriter() {
		return new LegacySignRewriter(
				Settings.getSignsLegacyWeaponBuy(), Settings.getSignsLegacyWeaponSell(),
				Settings.getSignsLegacyAmmoBuy(), Settings.getSignsLegacyAmmoSell(),
				Settings.getSignsLegacyWearableBuy(), Settings.getSignsLegacyWearableSell());
	}

	/**
	 * {@code manager.initialize()} is deliberately not called here: Keystone's convention pass calls
	 * {@code initialize()} on every non-{@code FileInitializer}/non-{@code BeanLifecycle} bean automatically, once
	 * every phase — and every module's beans — already exist. Calling it again here ran {@code setupSigns()}
	 * twice (harmless, since both sign registries are {@code Map.put}s, but still a real double-initialisation);
	 * deferring to the convention pass is also required for the sign-extension seam, since a module's
	 * {@code SignTypeContribution}/{@code SignViewProvider} beans are not guaranteed to exist yet at CONFIG-phase
	 * bean-construction time.
	 */
	@Bean
	public SignManager signManager(SignTypeRegistry signTypeRegistry, SignInteraction signInteraction,
	                               UniqueItemAddon uniqueItemAddon, ItemSerializerRegistry itemSerializerRegistry,
	                               ItemParser itemParser,
	                               @Qualifier("online") UserManager<Player> userManager,
	                               @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                               DependencyContainer container, LegacySignRewriter legacySignRewriter) {
		return new SignManager(gangland, Gangland.SHORT_PREFIX, signTypeRegistry, signInteraction,
		                       uniqueItemAddon, itemSerializerRegistry, itemParser, userManager, offlineUserManager,
		                       container, legacySignRewriter);
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

	/**
	 * {@link LootChestLoader} can't initialize during construction for the same reason as
	 * {@link #initializeInventoryLoader()}: {@link org.luckyraven.keystone.persistence.FileManager#initializeAll()}
	 * eagerly resolves item strings through module/plugin converters (weapon:awp, …) that don't exist yet inside the
	 * CONFIG phase. Deferring the call here (T-11) lets those converters register first.
	 */
	@PostConstruct
	public void initializeLootChestLoader() {
		FileManager fileManager = context.get(FileManager.class);
		if (fileManager != null) {
			fileManager.initializeAll();
		}
	}
}
