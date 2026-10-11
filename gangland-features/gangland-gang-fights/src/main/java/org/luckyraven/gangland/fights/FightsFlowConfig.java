package org.luckyraven.gangland.fights;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.fight.BukkitFightClock;
import org.luckyraven.gangland.fights.fight.FightClock;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Beans of the fight-flow lane (W6-S). Only that lane edits this class. */
@Configuration
public class FightsFlowConfig {

	@Bean
	public FightClock fightClock(JavaPlugin plugin) {
		return new BukkitFightClock(plugin);
	}

	@Bean
	public FightManager fightManager(FightClock clock) {
		return new FightManager();
	}

}
