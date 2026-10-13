package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

/** 0.20.0 lane patrol: the beat cops, the brandish warning clock and the pull-over tracker. */
@Configuration
public class PatrolModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public PatrolModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public BrandishWarnings brandishWarnings(CopLoader copLoader, CrimeService crimes, WantedMessages messages) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public PullOverTracker pullOverTracker(CopLoader copLoader, CrimeService crimes,
	                                       DetainmentEconomyContract economy, WantedMessages messages) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public PatrolServices patrolServices(CopLoader copLoader, CopPerception perception, TargetingManager targeting,
	                                     CopManager copManager, BrandishWarnings warnings, CopRadio radio) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public PatrolManager patrolManager(CopLoader copLoader, StationRegistry stations, CopSpawnManager spawner,
	                                   NpcMarkManager markManager, CopPerception perception) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Hands the services to the spawn manager and registers the beat cops with the cop manager. */
	@PostConstruct
	public void installPatrol() {
	}
}
