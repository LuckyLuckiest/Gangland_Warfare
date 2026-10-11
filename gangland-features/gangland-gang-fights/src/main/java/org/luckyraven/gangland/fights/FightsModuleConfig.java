package org.luckyraven.gangland.fights;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.command.fight.FightContribution;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * CONFIG-phase wiring shared by every fights lane: {@link FightRules} and the command contribution. The lane beans
 * live in {@link FightsArenaConfig}, {@link FightsFlowConfig} and {@link FightsEconomyConfig}.
 */
@Configuration
public class FightsModuleConfig {

	@Bean
	public FightRules fightRules(FightsModuleFiles files, FileManager fileManager) {
		FightRules rules = new FightRules(fileManager);
		fileManager.registerInitializer(rules);
		return rules;
	}

	/** Attaches {@code /glw gang fight ...} to the gang module's command (GangCommand queries path "gang"). */
	@Bean
	public FightContribution fightContribution(JavaPlugin plugin) {
		return new FightContribution(plugin);
	}

}
