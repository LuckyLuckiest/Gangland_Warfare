package org.luckyraven.gangland.config;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianService;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupOpenContract;
import org.luckyraven.gangland.copsncrooks.npc.turf.defender.TurfDefenderConfig;
import org.luckyraven.gangland.copsncrooks.npc.turf.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.copsncrooks.npc.turf.view.TurfPowerupBuffCatalogueView;
import org.luckyraven.gangland.copsncrooks.npc.turf.view.TurfPowerupFlow;
import org.luckyraven.gangland.copsncrooks.npc.turf.view.TurfPowerupGarrisonView;
import org.luckyraven.gangland.copsncrooks.npc.turf.view.TurfPowerupMenuView;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.database.repositories.turf.TurfPowerupNpcRepository;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.turf.TurfNpcContractImpl;
import org.luckyraven.gangland.file.configuration.turf.TurfNpcsConfigLoader;
import org.luckyraven.gangland.file.configuration.turf.TurfPowerupOpenContractImpl;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.turfnpcs.TurfNpcContract;

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
