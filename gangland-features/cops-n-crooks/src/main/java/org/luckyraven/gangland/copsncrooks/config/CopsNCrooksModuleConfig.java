package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.bail.BailService;
import org.luckyraven.gangland.copsncrooks.detainment.breakfree.BreakFreeService;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventory;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.*;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseExitContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.sentence.SentenceService;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandDetainmentMessages;
import org.luckyraven.gangland.copsncrooks.integration.detainment.*;
import org.luckyraven.gangland.copsncrooks.jail.*;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadioMessages;
import org.luckyraven.gangland.copsncrooks.npc.police.CopService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.WantedTargetingManager;
import org.luckyraven.gangland.copsncrooks.seam.CopsMoneyDropSource;
import org.luckyraven.gangland.copsncrooks.seam.HeatWantedTracker;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.data.economy.GanglandMoneyDropClassifier;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * CONFIG-phase wiring for cops-n-crooks (NPCs, jails, detainment). Moved from the core {@code CopsAndGadgetsConfig}
 * (T13, module split sprint 2026-09-07); the gadget-only remainder stays in core as {@code GadgetConfig}.
 *
 * <p>Group K (cops NPC base swap): the civilian NPC lifecycle beans this class used to declare
 * ({@code civiliansLoader}, {@code entityMarkManager}, {@code civilianNpcRegistry}, {@code civilianNpcFactory},
 * {@code civilianSpawnManager}, {@code civilianService}) moved to {@code gangland-civilians}'
 * {@code CiviliansModuleConfig} in group H; cops now injects {@link NpcMarkManager}, {@link BartizanNpcWeapons},
 * {@link DownedTargetFilter} and {@link CivilianNpcRegistry} from that module's beans through the shared container
 * (cops declares {@code Depends: [turf, civilians]}) rather than duplicating them here.
 *
 * <p>Highlights:
 * <ul>
 *     <li>{@link CopService} uses a no-arg constructor and a separate {@code initialize} call. The {@code @Bean}
 *     method body invokes that initializer directly so the LIFECYCLE pass doesn't try to call a non-existent
 *     zero-arg {@code initialize()}.</li>
 *     <li>{@code CopManager} takes {@link CivilianNpcRegistry} directly via constructor injection — no circular
 *     dependency or post-construction setter wiring needed.</li>
 * </ul>
 *
 * <p>{@link #installCoreSeams()} installs this module's delegates into the always-present core holder beans (seam 1:
 * {@link GanglandMoneyDropClassifier}; seam 3: {@link WantedKillTrackers}) once every bean above exists. It runs
 * inside {@code BeanFactory.instantiate()}, before {@code GanglandContext.runListenerPhase()} /
 * {@code runCommandPhase()}, so every consumer — listeners, commands, the placeholder bean's lazy reads — sees the
 * installed delegate. Seam 2, {@code BankTiers}, moved to {@code NpcShopsModuleConfig} in {@code gangland-npc-shops}
 * (T-J3, group J) — banker/trader NPCs and the bank tier catalogue left with them. The fourth seam,
 * {@code TurfNpcContracts}, is gone (group I): turf owns its own NPC managers now, so {@code GarrisonDeployListener}
 * injects {@code TurfDefenderDeployer}/{@code TurfPowerupManager} directly instead of going through a cross-module
 * bridge. See documentation/module-loader.md, "Core seams".
 */
@CustomLog
@Configuration
public class CopsNCrooksModuleConfig {

	private final JavaPlugin        plugin;
	private final DependencyContainer container;

	public CopsNCrooksModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin = plugin;
		this.container  = container;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Kill combo + jails + detainment
	// ---------------------------------------------------------------------------------------------------------------

	/** Kill_Counter comes from copsncrooks/wanted.yml (0.15.1); read once, as it was from settings.yml before. */
	@Bean
	public KillCombo killCombo(ChaseConfigLoader chaseConfig) {
		return new KillCombo(plugin, chaseConfig.getKillCombo().killCounter());
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
	public DetainmentService detainmentService(DetainmentRegistry detainmentRegistry, JailService jailService,
	                                           DetainmentMessageContract detainmentMessages,
	                                           PermissionManager permissionManager) {
		DetainmentService service = new DetainmentService(plugin, detainmentRegistry, jailService,
		                                                  jailService.getJailRegistry(), detainmentMessages,
		                                                  GanglandApi.FULL_PREFIX);
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

	/** copsncrooks/detainment.yml (0.15.1, formerly settings.yml Detainment). */
	@Bean
	public DetainmentSettings detainmentSettings(FileManager fileManager, Settings settings) {
		DetainmentSettings detainmentSettings = new DetainmentSettings(fileManager);
		fileManager.registerInitializer(detainmentSettings);
		fileManager.initializeAll();
		return detainmentSettings;
	}

	@Bean
	public DetainmentCostsContract detainmentCostsContract(DetainmentSettings detainmentSettings) {
		return new GanglandDetainmentCosts(detainmentSettings);
	}

	@Bean
	public DetainmentSoundContract detainmentSoundContract(DetainmentSettings detainmentSettings) {
		return new GanglandDetainmentSounds(detainmentSettings);
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
	public ReleaseExitContract releaseExitContract(JailExitRegistry jailExitRegistry, WaypointLookupContract waypointManager,
	                                               DetainmentSettings detainmentSettings) {
		return new GanglandReleaseExitContract(jailExitRegistry, waypointManager, detainmentSettings);
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
		return new PaperworkItem(plugin, detainmentMessages);
	}

	@Bean
	public TransitService transitService(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                                     DetainmentCostsContract costs) {
		return new TransitService(plugin, detainmentService, detainmentRegistry, costs);
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
	                                           DetainmentSoundContract sounds, DetainmentEconomyContract economy,
	                                           ChaseConfigLoader chase, HeatLedger ledger,
	                                           WantedMessages messages) {
		JailIntakeService intake = new JailIntakeService(detainmentService, detainmentRegistry, jailService,
		                                                 jailRegistry, seizedInventoryService, wantedClearContract,
		                                                 paperworkItemFactory, costs, sounds, economy, chase, ledger,
		                                                 messages);
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
		return new SentenceService(plugin, detainmentRegistry, detainmentService, releasePipeline, messages, sounds);
	}

	@Bean
	public BreakFreeService breakFreeService(DetainmentService detainmentService, DetainmentCostsContract costs,
	                                         DetainmentMessageContract messages, ReleasePipeline releasePipeline,
	                                         DetainmentSoundContract sounds, CrimeService crimes) {
		return new BreakFreeService(plugin, detainmentService, costs, messages, releasePipeline, sounds, crimes);
	}

	@Bean
	public HandcuffBribeView handcuffBribeView(InventoryService inventoryService, BribeService bribeService,
	                                           DetainmentEconomyContract economy,
	                                           MoneyIconProvider moneyIconProvider,
	                                           DetainmentMessageContract messages) {
		return new HandcuffBribeView(plugin, inventoryService, bribeService, economy, moneyIconProvider, messages);
	}

	@Bean
	public PaperworkView paperworkView(InventoryService inventoryService, DetainmentRegistry detainmentRegistry,
	                                   DetainmentCostsContract costs, DetainmentEconomyContract economy,
	                                   BailService bailService, BribeService bribeService,
	                                   SentenceService sentenceService, MoneyIconProvider moneyIconProvider,
	                                   DetainmentMessageContract messages, WantedMessages wantedMessages) {
		return new PaperworkView(plugin, inventoryService, detainmentRegistry, costs, economy, bailService,
		                         bribeService, sentenceService, moneyIconProvider, messages, wantedMessages);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Cop services
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CopLoader copLoader(ItemParser itemParser,
	                           CopSettings copSettings,
	                           FileManager fileManager) {
		CopLoader loader = new CopLoader(plugin, itemParser, copSettings,
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
	public CopSpawnManager copSpawnManager(CopLoader copLoader,
	                                       NpcMarkManager markManager,
	                                       BartizanNpcWeapons bartizanNpcWeapons,
	                                       DownedTargetFilter downedTargetFilter,
	                                       RepositoryRegistry repositoryRegistry,
	                                       DetainmentService detainmentService,
	                                       CuffLockRegistry cuffLockRegistry) {
		IRepository<CopSpawner> repo = repositoryRegistry.getRepository(CopSpawner.class);
		return new CopSpawnManager(plugin, copLoader, markManager, bartizanNpcWeapons, downedTargetFilter, repo,
		                           detainmentService, cuffLockRegistry);
	}

	@Bean
	public CopRadioMessages copRadioMessages(FileManager fileManager) {
		CopRadioMessages messages = new CopRadioMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	@Bean
	public CopRadio copRadio(CopLoader copLoader, CopRadioMessages copRadioMessages) {
		return new CopRadio(plugin, copLoader, copRadioMessages);
	}

	@Bean
	public CopManager copManager(CopSpawnManager copSpawnManager,
	                             WantedTargetingManager wantedTargetingManager,
	                             CopLoader copLoader,
	                             NpcMarkManager markManager,
	                             DetainmentService detainmentService,
	                             CivilianNpcRegistry civilianNpcRegistry,
	                             CopRadio copRadio) {
		return new CopManager(plugin, copSpawnManager, wantedTargetingManager, copLoader, markManager,
		                      detainmentService, civilianNpcRegistry, copRadio);
	}

	@Bean
	public CopService copService(CopManager copManager, WantedTargetingManager wantedTargetingManager) {
		return new CopService(copManager, wantedTargetingManager);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Core seam installation
	// ---------------------------------------------------------------------------------------------------------------

	@PostConstruct
	public void installCoreSeams() {
		// Runs inside BeanFactory.instantiate(), before GanglandContext's listener and command scans, so every
		// consumer (listeners, commands, the placeholder bean's lazy reads) sees the delegate.
		container.getInstance(GanglandMoneyDropClassifier.class)
		       .install(new CopsMoneyDropSource(container.getInstance(CopManager.class),
		                                        container.getInstance(CivilianNpcRegistry.class)));

		// Seam 2, BankTiers, moved to NpcShopsModuleConfig#installBankTiers() (T-J3, group J) — this module no
		// longer owns banker/trader NPCs or the bank tier catalogue.

		TurfManager         turfs     = container.getInstance(TurfManager.class);
		GangMembership      gangs     = container.getInstance(GangMembership.class);
		CivilianNpcRegistry civilians = container.getInstance(CivilianNpcRegistry.class);

		container.getInstance(WantedKillTrackers.class)
		       .install(new HeatWantedTracker(container.getInstance(ChaseConfigLoader.class),
		                                      container.getInstance(HeatLedger.class),
		                                      container.getInstance(CrimeService.class),
		                                      container.getInstance(KillCombo.class),
		                                      container.getInstance(NpcMarkManager.class), (killer, at) -> {
			                                      Turf turf = HeatLedger.contestedTurfAt(turfs, at);
			                                      int  gang = gangs.gangIdOf(killer.getUniqueId());
			                                      return turf != null && turf.getOwnerGangId() != null && gang != -1
			                                             && turf.getOwnerGangId() == gang;
		                                      }, victim -> civilians.getNpc(victim.getUniqueId()) != null));
	}
}
