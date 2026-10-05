package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/** Beans of the evasion clock (0.15.0): the clock itself and its two installs. */
@Configuration
public class EvasionModuleConfig {

	private final DependencyContainer container;

	public EvasionModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public EvasionClock evasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                                 WantedStars wantedStars, @Qualifier("online") UserManager<Player> users) {
		return new EvasionClock(config, copManager, detainment, wantedStars, users, System::currentTimeMillis,
		                        event -> Bukkit.getPluginManager().callEvent(event));
	}

	@PostConstruct
	public void installEvasion() {
		EvasionClock clock = container.getInstance(EvasionClock.class);
		container.getInstance(WantedStars.class).installDecayPolicy(clock);
		container.getInstance(CopManager.class).addAiTickHook(clock::tick);
	}
}
