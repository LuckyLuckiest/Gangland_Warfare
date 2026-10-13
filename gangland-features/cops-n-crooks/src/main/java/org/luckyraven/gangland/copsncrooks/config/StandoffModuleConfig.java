package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * Beans of the barricade standoff (0.21.0): the controller and its two cop-AI hooks.
 */
@Configuration
public class StandoffModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public StandoffModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public StandoffController standoffController(CopLoader copLoader, CopRadio copRadio, PerimeterController perimeter,
	                                             WantedStars wantedStars, @Qualifier("online") UserManager<Player> users,
	                                             DetainmentService detainment) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@PostConstruct
	public void installStandoff() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
