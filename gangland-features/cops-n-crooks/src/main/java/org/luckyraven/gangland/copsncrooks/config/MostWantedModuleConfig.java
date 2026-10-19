package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.mostwanted.MostWanted;
import org.luckyraven.gangland.copsncrooks.wanted.mostwanted.MostWantedPlaceholders;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.function.BiConsumer;

/**
 * 0.21.0 wave 2 (Most Wanted lane): empty configuration shell; the lane adds its beans here.
 */
@Configuration
public class MostWantedModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public MostWantedModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public MostWanted mostWanted(ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages, CopManager copManager,
	                             CopRadio copRadio) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public PlaceholderContribution mostWantedPlaceholders(MostWanted mostWanted) {
		return new MostWantedPlaceholders(mostWanted);
	}

	/** The police-radio call: only when the holder has a cop group to hear it. */
	BiConsumer<Player, Integer> radio(CopManager copManager, CopRadio copRadio) {
		return (player, stars) -> {
		};
	}

}
