package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpcRegistry;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansLoader;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpcFactory;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
import me.luckyraven.copsncrooks.npc.police.targeting.WantedTargetingManager;
import me.luckyraven.data.economy.GanglandMoneyDropClassifier;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.GanglandCivilianSpawnConfigProvider;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.ParkedCar;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.car.vehicle.VehicleRegistry;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.money.MoneyDropClassifier;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.weapon.WeaponManager;


/**
 * CONFIG-phase wiring for cops-n-crooks (NPCs, jails, detainment) plus the gadget services (car, jetpack, fuel) and the
 * money drop classifier that depends on cops + civilians.
 *
 * <p>Highlights:
 * <ul>
 *     <li>{@link #civiliansLoader(ItemParserManager, CivilianSettings, FileManager)}
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
	public CiviliansLoader civiliansLoader(ItemParserManager itemParserManager,
	                                       CivilianSettings civilianSettings,
	                                       FileManager fileManager) {
		CiviliansLoader loader = new CiviliansLoader(gangland, itemParserManager.getParser(), civilianSettings,
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
	public DetainmentService detainmentService(DetainmentRegistry detainmentRegistry, JailService jailService) {
		return new DetainmentService(gangland, detainmentRegistry, jailService,
		                             jailService.getJailRegistry(), Gangland.FULL_PREFIX);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Cop + civilian services
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CopLoader copLoader(ItemParserManager itemParserManager,
	                           CopSettings copSettings,
	                           FileManager fileManager) {
		CopLoader loader = new CopLoader(gangland, itemParserManager.getParser(), copSettings,
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
	                                             ItemParserManager itemParserManager,
	                                             WeaponManager weaponManager,
	                                             CivilianSettings civilianSettings) {
		return new CivilianNpcFactory(gangland, entityMarkManager, itemParserManager.getParser(), weaponManager,
		                              civilianSettings);
	}

	@Bean
	public CopSpawnManager copSpawnManager(CopLoader copLoader,
	                                       EntityMarkManager entityMarkManager,
	                                       WeaponManager weaponManager,
	                                       RepositoryRegistry repositoryRegistry,
	                                       DetainmentService detainmentService) {
		IRepository<CopSpawner> repo = repositoryRegistry.getRepository(CopSpawner.class);
		return new CopSpawnManager(gangland, copLoader, entityMarkManager, weaponManager, repo, detainmentService);
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
