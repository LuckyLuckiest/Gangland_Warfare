package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.WantedTargetingManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSpotting;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.Hideouts;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.QuietTrail;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseHabit;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLearner;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLevelStat;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Beans of the evasion clock (0.15.0): the clock itself and its two installs; since 0.15.2 also the chase arcs and the
 * learner that {@code Drop_Mode: AUTO} reads; since 0.16.0 also the containment perimeter, the hideouts and the cold trail.
 */
@Configuration
public class EvasionModuleConfig {

	private final DependencyContainer container;

	public EvasionModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public ChaseArcs chaseArcs() {
		return new ChaseArcs(System::currentTimeMillis);
	}

	@Bean
	public ChaseLearner chaseLearner(ChaseConfigLoader config, RepositoryRegistry repositoryRegistry) {
		return new ChaseLearner(config, repositoryRegistry.getRepository(ChaseHabit.class),
		                        repositoryRegistry.getRepository(ChaseLevelStat.class));
	}

	@Bean
	public Hideouts hideouts(PlaceNames placeNames, GangMembership gangMembership) {
		return new Hideouts(placeNames, gangMembership);
	}

	@Bean
	public QuietTrail quietTrail(HeatLedger heatLedger, ChaseArcs chaseArcs, ChaseConfigLoader config, CopRadio copRadio) {
		return new QuietTrail(heatLedger, chaseArcs, config, copRadio, System::currentTimeMillis);
	}

	@Bean
	public EvasionClock evasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                                 WantedStars wantedStars, @Qualifier("online") UserManager<Player> users,
	                                 HeatLedger heatLedger, ChaseArcs chaseArcs, ChaseLearner chaseLearner,
	                                 Hideouts hideouts, QuietTrail quietTrail) {
		return new EvasionClock(config, copManager, detainment, wantedStars, users, heatLedger, chaseArcs, chaseLearner,
		                        hideouts, quietTrail, System::currentTimeMillis,
		                        event -> Bukkit.getPluginManager().callEvent(event));
	}

	/**
	 * The post-escape search (0.16.1 T-187). The cops read the searching players through the targeting and the spawn
	 * guard, so a searched player is never cuffed.
	 */
	@Bean
	public PostEscapeSearch postEscapeSearch(ChaseConfigLoader config, @Qualifier("online") UserManager<Player> users,
	                                         WantedTargetingManager targeting, CopSpawnManager copSpawnManager,
	                                         CopManager copManager) {
		PostEscapeSearch search = new PostEscapeSearch(users, targeting, config::getPostEscape);
		copSpawnManager.setSearchGuard(targeting::isSearching);
		copManager.setEscapePredicate(search::isEscape);
		search.onGiveUp(copManager::searchGaveUp);
		copManager.addShutdownHook(search::endAll);
		return search;
	}

	/** A sighting of a searched player raises him by {@code Spotted_Stars} (0.16.1 wanted-1). */
	@Bean
	public PostEscapeSpotting postEscapeSpotting(ChaseConfigLoader config, @Qualifier("online") UserManager<Player> users,
	                                             WantedStars wantedStars, PostEscapeSearch search, CopRadio copRadio) {
		return new PostEscapeSpotting(search, wantedStars, users, config::getPostEscape, copRadio::now);
	}

	@Bean
	public PerimeterController perimeterController(CopLoader copLoader, CopManager copManager, CopRadio copRadio) {
		return new PerimeterController(copLoader::getLoadedProvider, copManager, copRadio, System::currentTimeMillis);
	}

	@PostConstruct
	public void installEvasion() {
		EvasionClock clock = container.getInstance(EvasionClock.class);
		container.getInstance(WantedStars.class).installDecayPolicy(clock);
		CopManager manager = container.getInstance(CopManager.class);
		manager.addAiTickHook(clock::tick);
		manager.addAiTickHook(container.getInstance(PostEscapeSpotting.class)::onAiTick);
		manager.addAiTickHook(container.getInstance(PerimeterController.class)::tick);
		manager.addAiTickHook(container.getInstance(QuietTrail.class)::tick);
	}
}
