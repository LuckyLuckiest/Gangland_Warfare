package org.luckyraven.gangland.copsncrooks.config;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.copsncrooks.database.TurfPowerupNpcRepository;
import org.luckyraven.gangland.copsncrooks.integration.turf.TurfNpcContractImpl;
import org.luckyraven.gangland.copsncrooks.integration.turf.TurfNpcsConfigLoader;
import org.luckyraven.gangland.copsncrooks.integration.turf.TurfPowerupOpenContractImpl;
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
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Bean wiring for the turf-system NPCs that live in cops-n-crooks: the per-turf Quartermaster (interactable powerup
 * vendor + hostile-on-contest civilian) and the auto-deploy garrison defenders. Both spawn through the existing
 * civilian NPC infrastructure — no bespoke entity types — so model/health/equipment/AI live entirely in
 * {@code civilians.yml}. Knobs specific to the turf system (which civilian type id, deploy radius, lifespan) live in
 * {@code turf/turf_npcs.yml} and are loaded by {@link TurfNpcsConfigLoader}.
 *
 * <p>Moved whole from the core {@code TurfNpcsConfig} (T15, module split sprint 2026-09-07) — every bean here
 * produces or wires a {@code copsncrooks.npc.turf} / {@code copsncrooks.npc.civilian} type, so there is no
 * turf-only remainder. {@link #turfNpcContractImpl} returns the concrete {@link TurfNpcContractImpl} (not the
 * {@code TurfNpcContract} interface) so nothing registers a second bean under that interface — the core
 * {@code TurfNpcContracts} holder (seam 4, {@code TurfConfig.turfNpcContracts()}) is the only producer of
 * {@code TurfNpcContract}, and {@code CopsNCrooksModuleConfig.installCoreSeams()} fetches this bean by its concrete
 * type to install into it.
 */
@Configuration
public final class TurfNpcsModuleConfig {

	private final Gangland gangland;

	public TurfNpcsModuleConfig(Gangland gangland) {
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
	public TurfNpcContractImpl turfNpcContractImpl(TurfDefenderDeployer defenders,
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
