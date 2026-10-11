package org.luckyraven.gangland.fights;

import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Beans of the arena lane (W6-A). Only that lane edits this class. */
@Configuration
public class FightsArenaConfig {

	@Bean
	public ArenaRegistry arenaRegistry(FightsModuleFiles files) {
		return new ArenaRegistry();
	}

}
