package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.bail.BailService;
import me.luckyraven.copsncrooks.detainment.breakfree.BreakFreeService;
import me.luckyraven.copsncrooks.detainment.bribe.BribeService;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentCostsContract;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract;
import me.luckyraven.copsncrooks.detainment.intake.JailIntakeService;
import me.luckyraven.copsncrooks.detainment.inventory.SeizedInventory;
import me.luckyraven.copsncrooks.detainment.inventory.SeizedInventoryService;
import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.copsncrooks.detainment.paperwork.*;
import me.luckyraven.copsncrooks.detainment.release.ReleaseExitContract;
import me.luckyraven.copsncrooks.detainment.release.ReleasePipeline;
import me.luckyraven.copsncrooks.detainment.sentence.SentenceService;
import me.luckyraven.copsncrooks.detainment.sound.DetainmentSoundContract;
import me.luckyraven.copsncrooks.detainment.transit.TransitService;
import me.luckyraven.copsncrooks.detainment.wanted.WantedClearContract;
import me.luckyraven.copsncrooks.jail.*;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpcRegistry;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansLoader;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpcFactory;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
import me.luckyraven.copsncrooks.npc.police.state.CuffLockRegistry;
import me.luckyraven.copsncrooks.npc.police.targeting.WantedTargetingManager;
import me.luckyraven.core.autowire.bean.Bean;
import me.luckyraven.core.autowire.bean.Configuration;
import me.luckyraven.core.autowire.bean.Qualifier;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.detainment.*;
import me.luckyraven.data.detainment.inventory.GanglandSeizedInventoryService;
import me.luckyraven.data.economy.GanglandMoneyDropClassifier;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.GanglandCivilianSpawnConfigProvider;
import me.luckyraven.file.configuration.copsncrooks.GanglandDetainmentMessages;
import me.luckyraven.file.configuration.gadget.GanglandCarMessages;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.ParkedCar;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.car.message.CarMessageContract;
import me.luckyraven.gadget.car.vehicle.VehicleRegistry;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.ItemParser;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDropClassifier;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.entity.Player;


/**
 * CONFIG-phase wiring for cops-n-crooks (NPCs, jails, detainment) plus the gadget services (car, jetpack, fuel) and the
 * money drop classifier that depends on cops + civilians.
 *
 * <p>Highlights:
 * <ul>
 *     <li>{@link #civiliansLoader(ItemParser, CivilianSettings, FileManager)}
 *     binds + registers + loads in one go because the entity-mark manager downstream reads {@code getLoadedConfig()}
 *     immediately.</li>
 *     <li>{@link CopService} and {@link CivilianService} use no-arg constructors and a separate {@code initialize}
 *     call. The {@code @Bean} method body invokes that initializer directly so the LIFECYCLE pass doesn't try to
 *     call a non-existent zero-arg {@code initialize()}.</li>
 *     <li>{@code CopManager} takes {@link CivilianNpcRegistry} directly via constructor injection — no circular
 *     dependency or post-construction setter wiring needed.</li>
 * </ul>
 */
@CustomLog
@Configuration
public class CopsAndGadgetsConfig {

	private final Gangland gangland;

	public CopsAndGadgetsConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Civilians + entity marks
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CiviliansLoader civiliansLoader(ItemParser itemParser,
	                                       CivilianSettings civilianSettings,
	                                       FileManager fileManager) {
		CiviliansLoader loader = new CiviliansLoader(gangland, itemParser, civilianSettings,
		                                             false, null, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public EntityMarkManager entityMarkManager(CiviliansLoader civiliansLoader) {
		return new EntityMarkManager(gangland, civiliansLoader);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Kill combo + jails + detainment
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public KillCombo killCombo(Settings settings) {
		return new KillCombo(gangland, Settings.getWantedKillCounter());
	}

	@Bean
	public JailRegistry jailRegistry() {
		return new JailRegistry();
	}

	@Bean
	public JailService jailService(JailRegistry jailRegistry, RepositoryRegistry repositoryRegistry) {
		IRepository<Jail> jailRepository = repositoryRegistry.getRepository(Jail.class);
		return new JailService(jailRegistry, jailRepository);
	}

	@Bean
	public DetainmentRegistry detainmentRegistry(JailRegistry jailRegistry, RepositoryRegistry repositoryRegistry) {
		IRepository<DetainedPlayer> detainmentRepository = repositoryRegistry.getRepository(DetainedPlayer.class);
		return new DetainmentRegistry(detainmentRepository, jailRegistry);
	}

	@Bean
	public DetainmentMessageContract detainmentMessageContract() {
		return new GanglandDetainmentMessages();
	}

	@Bean
	public CarMessageContract carMessageContract() {
		return new GanglandCarMessages();
	}

	@Bean
	public DetainmentService detainmentService(DetainmentRegistry detainmentRegistry, JailService jailService,
	                                           DetainmentMessageContract detainmentMessages,
	                                           PermissionManager permissionManager) {
		DetainmentService service = new DetainmentService(gangland, detainmentRegistry, jailService,
		                                                  jailService.getJailRegistry(), detainmentMessages,
		                                                  Gangland.FULL_PREFIX);
		// Register the bypass permission so permission plugins (LuckPerms, etc.) can see it.
		permissionManager.addPermission(service.getCommandBypassPermission());
		return service;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Detainment cuff → jail → bail/bribe/sentence feature wiring
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CuffLockRegistry cuffLockRegistry() {
		return new CuffLockRegistry();
	}

	@Bean
	public JailExitRegistry jailExitRegistry() {
		return new JailExitRegistry();
	}

	@Bean
	public JailExitService jailExitService(JailExitRegistry jailExitRegistry, RepositoryRegistry repositoryRegistry) {
		IRepository<JailExit> repository = repositoryRegistry.getRepository(JailExit.class);
		return new JailExitService(jailExitRegistry, repository);
	}

	@Bean
	public DetainmentCostsContract detainmentCostsContract() {
		return new GanglandDetainmentCosts();
	}

	@Bean
	public DetainmentSoundContract detainmentSoundContract() {
		return new GanglandDetainmentSounds();
	}

	@Bean
	public WantedClearContract wantedClearContract(@Qualifier("online") UserManager<Player> userManager) {
		return new GanglandWantedClearContract(userManager);
	}

	@Bean
	public DetainmentEconomyContract detainmentEconomyContract(@Qualifier("online") UserManager<Player> userManager) {
		return new GanglandDetainmentEconomyContract(userManager);
	}

	@Bean
	public ReleaseExitContract releaseExitContract(JailExitRegistry jailExitRegistry, WaypointManager waypointManager) {
		return new GanglandReleaseExitContract(jailExitRegistry, waypointManager);
	}

	@Bean
	public MoneyIconProvider moneyIconProvider(MoneyAddon moneyAddon) {
		return new GanglandMoneyIconProvider(moneyAddon);
	}

	@Bean
	public SeizedInventoryService seizedInventoryService(RepositoryRegistry repositoryRegistry) {
		IRepository<SeizedInventory> repository = repositoryRegistry.getRepository(SeizedInventory.class);
		return new GanglandSeizedInventoryService(repository);
	}

	@Bean
	public PaperworkItemFactory paperworkItemFactory(DetainmentMessageContract detainmentMessages) {
		return new PaperworkItem(gangland, detainmentMessages);
	}

	@Bean
	public TransitService transitService(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                                     DetainmentCostsContract costs) {
		return new TransitService(gangland, detainmentService, detainmentRegistry, costs);
	}

	@Bean
	public ReleasePipeline releasePipeline(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                                       SeizedInventoryService seizedInventoryService,
	                                       TransitService transitService,
	                                       PaperworkItemFactory paperworkItemFactory,
	                                       ReleaseExitContract releaseExitContract,
	                                       KillCombo killCombo) {
		return new ReleasePipeline(detainmentService, detainmentRegistry, seizedInventoryService, transitService,
		                           paperworkItemFactory, releaseExitContract, killCombo);
	}

	@Bean
	public JailIntakeService jailIntakeService(DetainmentService detainmentService,
	                                           DetainmentRegistry detainmentRegistry,
	                                           JailService jailService, JailRegistry jailRegistry,
	                                           SeizedInventoryService seizedInventoryService,
	                                           WantedClearContract wantedClearContract,
	                                           PaperworkItemFactory paperworkItemFactory,
	                                           DetainmentCostsContract costs,
	                                           TransitService transitService,
	                                           DetainmentSoundContract sounds) {
		JailIntakeService intake = new JailIntakeService(detainmentService, detainmentRegistry, jailService,
		                                                 jailRegistry, seizedInventoryService, wantedClearContract,
		                                                 paperworkItemFactory, costs, sounds);
		// Wire the transit→intake callback here to break the construction cycle
		// (TransitService already exists as a bean; its onCommit is set lazily.)
		transitService.setOnCommit(intake::admit);
		return intake;
	}

	@Bean
	public BribeService bribeService(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                                 DetainmentCostsContract costs, DetainmentEconomyContract economy,
	                                 WantedClearContract wantedClearContract, ReleasePipeline releasePipeline,
	                                 CuffLockRegistry cuffLockRegistry, DetainmentSoundContract sounds) {
		return new BribeService(detainmentService, detainmentRegistry, costs, economy, wantedClearContract,
		                        releasePipeline, cuffLockRegistry, sounds);
	}

	@Bean
	public BailService bailService(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                               DetainmentCostsContract costs, DetainmentEconomyContract economy,
	                               ReleasePipeline releasePipeline, DetainmentSoundContract sounds) {
		return new BailService(detainmentService, detainmentRegistry, costs, economy, releasePipeline, sounds);
	}

	@Bean
	public SentenceService sentenceService(DetainmentRegistry detainmentRegistry, DetainmentService detainmentService,
	                                       ReleasePipeline releasePipeline, DetainmentMessageContract messages,
	                                       DetainmentSoundContract sounds) {
		return new SentenceService(gangland, detainmentRegistry, detainmentService, releasePipeline, messages, sounds);
	}

	@Bean
	public BreakFreeService breakFreeService(DetainmentService detainmentService, DetainmentCostsContract costs,
	                                         DetainmentMessageContract messages, ReleasePipeline releasePipeline,
	                                         DetainmentSoundContract sounds) {
		return new BreakFreeService(gangland, detainmentService, costs, messages, releasePipeline, sounds);
	}

	@Bean
	public HandcuffBribeView handcuffBribeView(BribeService bribeService, DetainmentEconomyContract economy,
	                                           MoneyIconProvider moneyIconProvider,
	                                           DetainmentMessageContract messages) {
		return new HandcuffBribeView(gangland, bribeService, economy, moneyIconProvider, messages);
	}

	@Bean
	public PaperworkView paperworkView(DetainmentRegistry detainmentRegistry, DetainmentCostsContract costs,
	                                   DetainmentEconomyContract economy, BailService bailService,
	                                   BribeService bribeService, SentenceService sentenceService,
	                                   MoneyIconProvider moneyIconProvider,
	                                   DetainmentMessageContract messages) {
		return new PaperworkView(gangland, detainmentRegistry, costs, economy, bailService, bribeService,
		                         sentenceService, moneyIconProvider, messages);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Cop + civilian services
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CopLoader copLoader(ItemParser itemParser,
	                           CopSettings copSettings,
	                           FileManager fileManager) {
		CopLoader loader = new CopLoader(gangland, itemParser, copSettings,
		                                 false, null, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public WantedTargetingManager wantedTargetingManager() {
		return new WantedTargetingManager();
	}

	@Bean
	public CivilianNpcRegistry civilianNpcRegistry() {
		return new CivilianNpcRegistry();
	}

	@Bean
	public CivilianNpcFactory civilianNpcFactory(EntityMarkManager entityMarkManager,
	                                             ItemParser itemParser,
	                                             WeaponManager weaponManager,
	                                             CivilianSettings civilianSettings) {
		return new CivilianNpcFactory(gangland, entityMarkManager, itemParser, weaponManager,
		                              civilianSettings);
	}

	@Bean
	public CopSpawnManager copSpawnManager(CopLoader copLoader,
	                                       EntityMarkManager entityMarkManager,
	                                       WeaponManager weaponManager,
	                                       RepositoryRegistry repositoryRegistry,
	                                       DetainmentService detainmentService,
	                                       CuffLockRegistry cuffLockRegistry) {
		IRepository<CopSpawner> repo = repositoryRegistry.getRepository(CopSpawner.class);
		return new CopSpawnManager(gangland, copLoader, entityMarkManager, weaponManager, repo, detainmentService,
		                           cuffLockRegistry);
	}

	@Bean
	public CopManager copManager(CopSpawnManager copSpawnManager,
	                             WantedTargetingManager wantedTargetingManager,
	                             CopLoader copLoader,
	                             EntityMarkManager entityMarkManager,
	                             DetainmentService detainmentService,
	                             CivilianNpcRegistry civilianNpcRegistry) {
		return new CopManager(gangland, copSpawnManager, wantedTargetingManager, copLoader, entityMarkManager,
		                      detainmentService, civilianNpcRegistry);
	}

	@Bean
	public CopService copService(CopManager copManager, WantedTargetingManager wantedTargetingManager) {
		return new CopService(copManager, wantedTargetingManager);
	}

	@Bean
	public CivilianSpawnManager civilianSpawnManager(CivilianNpcFactory civilianNpcFactory,
	                                                 CivilianNpcRegistry civilianNpcRegistry,
	                                                 CiviliansLoader civiliansLoader,
	                                                 GanglandCivilianSpawnConfigProvider spawnConfigProvider,
	                                                 RepositoryRegistry repositoryRegistry) {
		IRepository<CivilianSpawner> repo = repositoryRegistry.getRepository(CivilianSpawner.class);
		return new CivilianSpawnManager(spawnConfigProvider, repo, civilianNpcFactory, civilianNpcRegistry,
		                                civiliansLoader);
	}

	@Bean
	public CivilianService civilianService(CiviliansLoader civiliansLoader,
	                                       EntityMarkManager entityMarkManager,
	                                       CivilianSettings civilianSettings,
	                                       CivilianNpcFactory civilianNpcFactory,
	                                       CivilianSpawnManager civilianSpawnManager,
	                                       CivilianNpcRegistry civilianNpcRegistry) {
		return new CivilianService(gangland, civiliansLoader, entityMarkManager, civilianSettings,
		                           civilianNpcFactory, civilianSpawnManager, civilianNpcRegistry);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Gadgets: car, jetpack, fuel pulls from FILE phase
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CarService carService(CarAddon carAddon,
	                             RepositoryRegistry repositoryRegistry,
	                             FuelService fuelService,
	                             GadgetPhysicsConfig gadgetPhysicsConfig) {
		IRepository<ParkedCar> parkedCarRepository = repositoryRegistry.getRepository(ParkedCar.class);
		CarService carService = new CarService(carAddon, new VehicleRegistry(), gangland, parkedCarRepository,
		                                       fuelService, gadgetPhysicsConfig);
		carService.reloadParkedVehicles();
		return carService;
	}

	@Bean
	public JetpackService jetpackService(FuelService fuelService,
	                                     GadgetPhysicsConfig gadgetPhysicsConfig,
	                                     WearableAddon wearableAddon) {
		return new JetpackService(fuelService, gangland, gadgetPhysicsConfig, wearableAddon);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Money drop classifier (cross-cuts cops + civilians)
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public MoneyDropClassifier moneyDropClassifier(CopManager copManager, CivilianNpcRegistry civilianNpcRegistry) {
		return new GanglandMoneyDropClassifier(copManager, civilianNpcRegistry);
	}
}
