package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.repositories.lootchest.LootChestRepository;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.lootchest.GanglandLootChestMessages;
import me.luckyraven.file.configuration.lootchest.LootChestSettings;
import me.luckyraven.file.configuration.weapon.GanglandBlockRegenerationSettings;
import me.luckyraven.file.configuration.weapon.GanglandRepairMessages;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.repair.GanglandRepairService;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.anvil.RepairAnvilGui;
import me.luckyraven.gadget.repair.config.RepairLoader;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.hologram.HologramService;
import me.luckyraven.item.ItemParser;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.contract.*;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.config.LootChestLoader;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.money.GanglandMoneyDepositService;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.sign.SignManager;
import me.luckyraven.sign.bulk.BulkActionManager;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignFormatterService;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.PostConstruct;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.modifiers.BlockDamageManager;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponVisualSpawner;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

/**
 * CONFIG-phase wiring for the gameplay-side managers: weapons, signs, items, inventory, hologram, loot chest, repair,
 * money. Every bean here can constructor-inject any FILE-phase or DATABASE-phase bean by type.
 *
 * <p>The structural ordering inside the topo sort:
 * <ol>
 *     <li>Weapon system: {@link WeaponManager} → {@link BlockDamageManager} / {@link WeaponVisualSpawner} →
 *     {@link WeaponRaytracer} (depends on the previous three).</li>
 *     <li>Sign system: registries → {@link SignFormatterService} → {@link SignInteraction} → {@link SignManager}
 *     and {@link BulkActionManager}.</li>
 *     <li>Money + items: {@link MoneyDepositService} (must precede the parser; {@code MoneyConverter} resolves the
 *     currency symbol via the contract on instantiation), then {@link ItemParserManager}, which the loot chest +
 *     cops-n-crooks beans transitively consume.</li>
 *     <li>Loot chest: {@link HologramService} → {@link LootChestManager} → {@link LootChestLoader}.</li>
 *     <li>Repair: {@link RepairLoader}, {@link RepairManager}, {@link RepairAnvilGui} → {@link GanglandRepairService}.
 * </li>
 * </ol>
 *
 * <p>Tiny bridge / contract beans (those used to be the manual {@code dependencyContainer.registerInstance(...)}
 * dump in the legacy {@code Initializer.events()}) live here too because they're trivial wrappers over the managers
 * they bind to.
 */
@CustomLog
@Configuration
public class GameplayConfig {

	private final Gangland gangland;

	public GameplayConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Scoreboard
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public ScoreboardManager scoreboardManager() {
		return new ScoreboardManager(gangland);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Weapon system
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public WeaponManager weaponManager() {
		WeaponManager manager = new WeaponManager(gangland);
		manager.initialize();
		return manager;
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
	public WeaponRaytracer weaponRaytracer(WeaponManager weaponManager,
	                                       WearableAddon wearableAddon,
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
	public SignManager signManager(SignTypeRegistry signTypeRegistry, SignInteraction signInteraction) {
		SignManager manager = new SignManager(gangland, Gangland.SHORT_PREFIX, signTypeRegistry, signInteraction);
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
	                                               MoneyAddon moneyAddon) {
		return new GanglandMoneyDepositService(gangland, userManager, moneyAddon);
	}

	@Bean
	public ItemParserManager itemParserManager(WeaponManager weaponManager,
	                                           AmmunitionManager ammunitionManager,
	                                           WearableAddon wearableAddon,
	                                           CarAddon carAddon,
	                                           MoneyAddon moneyAddon,
	                                           MoneyDepositService moneyDepositService,
	                                           UniqueItemAddon uniqueItemAddon) {
		return new ItemParserManager(weaponManager, ammunitionManager, wearableAddon, carAddon,
		                             moneyAddon, moneyDepositService, uniqueItemAddon);
	}

	@Bean
	public ItemParser itemParser(ItemParserManager itemParserManager) {
		return itemParserManager.getParser();
	}

	@Bean
	public UniqueItemRegistry uniqueItemRegistry(UniqueItemAddon uniqueItemAddon) {
		return uniqueItemAddon;
	}

	@Bean
	public UniqueItemInteractionService uniqueItemInteractionService() {
		return new GanglandUniqueItemInteractionService(gangland);
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
	                                         ItemParserManager itemParserManager) {
		LootChestManager           manager = new LootChestManager(gangland, Gangland.FULL_PREFIX, hologramService);
		IRepository<LootChestData> repo    = repositoryRegistry.getRepository(LootChestData.class);
		if (!(repo instanceof LootChestRepository castedRepo)) {
			throw new PluginException("LootChestData repository is not initialized!");
		}
		manager.initialize(castedRepo, false);
		manager.setItemParser(itemParserManager.getParser());
		manager.setMessagesProvider(new GanglandLootChestMessages());
		return manager;
	}

	@Bean
	public LootChestService lootChestService(LootChestManager lootChestManager) {
		return lootChestManager;
	}

	@Bean
	public LootChestLoader lootChestLoader(LootChestManager lootChestManager, FileManager fileManager) {
		LootChestLoader loader = new LootChestLoader(gangland, lootChestManager, new LootChestSettings());
		loader.bind(false, null, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Repair
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public RepairManager repairManager(me.luckyraven.data.placeholder.PlaceholderService placeholderService,
	                                   FileManager fileManager) {
		RepairManager repairManager = new RepairManager();
		repairManager.setPlaceholder(placeholderService);
		RepairLoader repairLoader = new RepairLoader(gangland);
		repairLoader.bind(false, repairManager::load, fileManager);
		fileManager.registerInitializer(repairLoader);
		fileManager.initializeAll();
		repairManager.setMessages(new GanglandRepairMessages());
		return repairManager;
	}

	@Bean
	public RepairAnvilGui repairAnvilGui(RepairManager repairManager) {
		return new RepairAnvilGui(gangland, repairManager);
	}

	@Bean
	public RepairService repairService(WeaponManager weaponManager, RepairAnvilGui repairAnvilGui) {
		return new GanglandRepairService(gangland, weaponManager, repairAnvilGui);
	}

	/**
	 * {@link me.luckyraven.file.configuration.inventory.InventoryLoader} can't initialize during the FILE phase because
	 * its load callback parses prefixed item refs (weapon:awp, wearable:police_vest, …) via {@link ItemParserManager},
	 * which is a CONFIG-phase bean. By the time this {@code @PostConstruct} runs, every bean is registered AND the
	 * per-bean hydrate hook has populated {@code Initializer.itemParserManager}, so the {@code SlotItemFactory}
	 * resolver lambda set up in {@code FileConfig.inventoryLoader()} can dereference it.
	 */
	@PostConstruct
	public void initializeInventoryLoader() {
		me.luckyraven.file.configuration.inventory.InventoryLoader loader =
				gangland.getInitializer()
				        .getContext()
				        .get(me.luckyraven.file.configuration.inventory.InventoryLoader.class);
		if (loader != null) {
			loader.initialize();
		}
	}
}
