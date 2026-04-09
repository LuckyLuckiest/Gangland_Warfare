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
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansLoader;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
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
import me.luckyraven.money.GanglandMoneyDropClassifier;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.weapon.WeaponManager;

import java.util.ArrayList;

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
 *     <li>{@code civilianService} takes {@link CopService} as a parameter so the topo sort builds it first, then
 *     wires {@code copManager.setCivilianService(civilianService)} inline before returning — replaces the manual
 *     post-construction call in the legacy {@code postInitialize()}.</li>
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
		CiviliansLoader loader = new CiviliansLoader(gangland, itemParserManager.getParser(), civilianSettings);
		loader.bind(false, null, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public EntityMarkManager entityMarkManager(CiviliansLoader civiliansLoader) {
		return new EntityMarkManager(gangland,
		                             civiliansLoader.getLoadedConfig().defaultPoliceEntities(),
		                             civiliansLoader.getLoadedConfig().defaultCivilianEntities());
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
		CopLoader loader = new CopLoader(gangland, itemParserManager.getParser(), copSettings);
		loader.bind(false, null, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public CopService copService(CopLoader copLoader,
	                             EntityMarkManager entityMarkManager,
	                             WeaponManager weaponManager,
	                             RepositoryRegistry repositoryRegistry,
	                             DetainmentService detainmentService) {
		CopService              service = new CopService();
		IRepository<CopSpawner> repo    = repositoryRegistry.getRepository(CopSpawner.class);
		service.initialize(gangland, copLoader.getLoadedProvider(), entityMarkManager, weaponManager, repo,
		                   detainmentService);
		return service;
	}

	@Bean
	public CopManager copManager(CopService copService) {
		return copService.getCopManager();
	}

	@Bean
	public CopSpawnManager copSpawnManager(CopService copService) {
		return copService.getCopManager().getSpawnManager();
	}

	@Bean
	public CivilianService civilianService(CopService copService,
	                                       CiviliansLoader civiliansLoader,
	                                       EntityMarkManager entityMarkManager,
	                                       RepositoryRegistry repositoryRegistry,
	                                       CivilianSettings civilianSettings,
	                                       GanglandCivilianSpawnConfigProvider spawnConfigProvider,
	                                       ItemParserManager itemParserManager,
	                                       WeaponManager weaponManager) {
		CivilianService              service = new CivilianService();
		IRepository<CivilianSpawner> repo    = repositoryRegistry.getRepository(CivilianSpawner.class);
		service.initialize(gangland, civiliansLoader.getLoadedConfig(), entityMarkManager, repo,
		                   civilianSettings, spawnConfigProvider, itemParserManager.getParser(), weaponManager);
		// Wire the civilian service into the cop manager so cops can pursue wanted hostile civilians. The CopService
		// param above forces the topo sort to build it before this bean runs.
		copService.getCopManager().setCivilianService(service);
		return service;
	}

	@Bean
	public CivilianSpawnManager civilianSpawnManager(CivilianService civilianService) {
		return civilianService.getSpawnManager();
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
		parkedCarRepository.setDataSupplier(() -> new ArrayList<>(carService.getParkedCarRecords().values()));
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
	public MoneyDropClassifier moneyDropClassifier(CopService copService, CivilianService civilianService) {
		return new GanglandMoneyDropClassifier(copService, civilianService);
	}
}
