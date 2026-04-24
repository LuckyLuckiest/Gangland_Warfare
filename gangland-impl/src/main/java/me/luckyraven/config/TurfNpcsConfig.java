package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupManager;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupOpenContract;
import me.luckyraven.copsncrooks.npc.turf.defender.TurfDefenderConfig;
import me.luckyraven.copsncrooks.npc.turf.defender.TurfDefenderDeployer;
import me.luckyraven.copsncrooks.npc.turf.view.TurfPowerupBuffCatalogueView;
import me.luckyraven.copsncrooks.npc.turf.view.TurfPowerupFlow;
import me.luckyraven.copsncrooks.npc.turf.view.TurfPowerupGarrisonView;
import me.luckyraven.copsncrooks.npc.turf.view.TurfPowerupMenuView;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.database.repositories.turf.TurfPowerupNpcRepository;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.turf.TurfNpcContractImpl;
import me.luckyraven.file.configuration.turf.TurfNpcsConfigLoader;
import me.luckyraven.file.configuration.turf.TurfPowerupOpenContractImpl;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.powerups.ActiveBuffManager;
import me.luckyraven.turf.powerups.GarrisonManager;
import me.luckyraven.turf.powerups.PowerupRegistry;
import me.luckyraven.turf.turfnpcs.TurfNpcContract;

/**
 * Bean wiring for the turf-system NPCs that live in cops-n-crooks: the per-turf Quartermaster (interactable powerup
 * vendor + hostile-on-contest civilian) and the auto-deploy garrison defenders. Both spawn through the existing
 * civilian NPC infrastructure — no bespoke entity types — so model/health/equipment/AI live entirely in
 * {@code civilians.yml}. Knobs specific to the turf system (which civilian type id, deploy radius, lifespan) live in
 * {@code turf/turf_npcs.yml} and are loaded by {@link TurfNpcsConfigLoader}.
 */
@Configuration
public final class TurfNpcsConfig {

	private final Gangland gangland;

	public TurfNpcsConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public TurfNpcsConfigLoader turfNpcsConfigLoader(FileManager fileManager) {
		return new TurfNpcsConfigLoader(fileManager);
	}

	@Bean
	public TurfDefenderConfig turfDefenderConfig(TurfNpcsConfigLoader loader) {
		return loader.getDefenderConfig();
	}

	@Bean
	public TurfDefenderDeployer turfDefenderDeployer(CivilianService civilianService,
	                                                 CivilianSpawnManager spawnManager) {
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(gangland, civilianService, spawnManager);
		deployer.start();
		return deployer;
	}

	@Bean
	public TurfPowerupManager turfPowerupManager(TurfPowerupNpcRepository repository,
	                                             TurfNpcsConfigLoader loader,
	                                             CivilianSpawnManager spawnManager) {
		return new TurfPowerupManager(gangland, repository, loader.getPowerupSettings(), spawnManager);
	}

	@Bean
	public TurfNpcContract turfNpcContract(TurfDefenderDeployer defenders,
	                                       TurfDefenderConfig defenderConfig,
	                                       TurfPowerupManager powerupNpcs,
	                                       GangLookupContract gangs) {
		return new TurfNpcContractImpl(defenders, defenderConfig, powerupNpcs, gangs);
	}

	@Bean
	public TurfPowerupMenuView turfPowerupMenuView(@SuppressWarnings("unused") Settings settings,
	                                               GarrisonManager garrisons, ActiveBuffManager buffs) {
		return new TurfPowerupMenuView(garrisons, buffs,
		                               Settings.getInventoryFillItem(), Settings.getInventoryFillName());
	}

	@Bean
	public TurfPowerupBuffCatalogueView turfPowerupBuffCatalogueView(@SuppressWarnings("unused") Settings settings,
	                                                                 PowerupRegistry registry,
	                                                                 ActiveBuffManager buffs) {
		return new TurfPowerupBuffCatalogueView(registry, buffs,
		                                        Settings.getInventoryFillItem(),
		                                        Settings.getInventoryFillName());
	}

	@Bean
	public TurfPowerupGarrisonView turfPowerupGarrisonView(@SuppressWarnings("unused") Settings settings,
	                                                       GarrisonManager garrisons) {
		return new TurfPowerupGarrisonView(garrisons,
		                                   Settings.getInventoryFillItem(), Settings.getInventoryFillName());
	}

	@Bean
	public TurfPowerupFlow turfPowerupFlow(TurfPowerupMenuView menuView,
	                                       TurfPowerupBuffCatalogueView buffsView,
	                                       TurfPowerupGarrisonView garrisonView) {
		return new TurfPowerupFlow(gangland, menuView, buffsView, garrisonView);
	}

	@Bean
	public TurfPowerupOpenContract turfPowerupOpenContract(TurfPowerupFlow flow,
	                                                       TurfManager turfs,
	                                                       TurfPowerupManager npcs,
	                                                       GangLookupContract gangs,
	                                                       UserLookupContract users) {
		return new TurfPowerupOpenContractImpl(flow, turfs, npcs, gangs, users);
	}
}
