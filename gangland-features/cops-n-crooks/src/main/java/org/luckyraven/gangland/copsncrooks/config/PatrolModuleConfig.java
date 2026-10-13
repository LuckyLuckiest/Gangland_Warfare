package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.HeldWeapon;
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
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

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
		return new BrandishWarnings(() -> copLoader.getLoadedProvider().getWarningSettings(), System::currentTimeMillis,
		                            crimes, p -> Settings.isBartizanAvailable() && HeldWeapon.holding(p), messages);
	}

	@Bean
	public PullOverTracker pullOverTracker(CopLoader copLoader, CrimeService crimes,
	                                       DetainmentEconomyContract economy, WantedMessages messages) {
		return new PullOverTracker(plugin, () -> copLoader.getLoadedProvider().getPullOverSettings(),
		                           System::currentTimeMillis, crimes, economy, messages, Bukkit::getPlayer,
		                           amount -> money(amount));
	}

	@Bean
	public PatrolServices patrolServices(CopLoader copLoader, CopPerception perception, TargetingManager targeting,
	                                     CopManager copManager, BrandishWarnings warnings, CopRadio radio) {
		return new PatrolServices(() -> copLoader.getLoadedProvider().getPatrolSettings(),
		                          () -> copLoader.getLoadedProvider().getWarningSettings(), perception,
		                          p -> targeting.isWanted(p.getUniqueId()), copManager::groupOf, warnings, radio,
		                          () -> Bukkit.getOnlinePlayers(), NpcSupport::isNpc, System::currentTimeMillis,
		                          new Random());
	}

	@Bean
	public PatrolManager patrolManager(CopLoader copLoader, StationRegistry stations, CopSpawnManager spawner,
	                                   NpcMarkManager markManager, CopPerception perception) {
		return new PatrolManager(plugin, () -> copLoader.getLoadedProvider().getPatrolSettings(), stations, spawner,
		                         markManager, perception, () -> Bukkit.getOnlinePlayers(), NpcSupport::isNpc,
		                         Settings::isCitizensAvailable, System::currentTimeMillis);
	}

	/** Hands the services to the spawn manager and registers the beat cops with the cop manager. */
	@PostConstruct
	public void installPatrol() {
		container.getInstance(CopSpawnManager.class).setPatrolServices(container.getInstance(PatrolServices.class));
		container.getInstance(CopManager.class).addCopResolver(container.getInstance(PatrolManager.class)::beatCopOf);
	}

	/** Null-safe: the money symbol is unset until settings.yml loads, and {@code Map.of} rejects nulls. */
	private static Map<String, String> money(double amount) {
		return Map.of("money_symbol", Objects.requireNonNullElse(Settings.getMoneySymbol(), ""), "amount",
		              Settings.formatAmount(BigDecimal.valueOf(amount)));
	}
}
