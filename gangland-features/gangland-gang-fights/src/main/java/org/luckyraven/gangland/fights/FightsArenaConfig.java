package org.luckyraven.gangland.fights;

import org.luckyraven.gangland.fights.arena.ArenaDrafts;
import org.luckyraven.gangland.fights.arena.ArenaMessages;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.FileManager;

import java.nio.file.Path;

/** Beans of the arena lane (W6-A). Only that lane edits this class. */
@Configuration
public class FightsArenaConfig {

	/** Reads {@code fight/arenas.yml} from the data folder now and again on {@code /glw arena reload}. */
	@Bean
	public ArenaRegistry arenaRegistry(FightsModuleFiles files) {
		return new ArenaRegistry(Path.of(files.arenas().getDirectory()));
	}

	@Bean
	public ArenaDrafts arenaDrafts() {
		return new ArenaDrafts();
	}

	@Bean
	public ArenaMessages arenaMessages(FightsModuleFiles files, FileManager fileManager) {
		return new ArenaMessages(fileManager);
	}

}
