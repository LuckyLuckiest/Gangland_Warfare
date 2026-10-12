package org.luckyraven.gangland.fights;

import lombok.CustomLog;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the gang fights module ({@code module.yml} {@code Main}, needs the {@code gang} module). One
 * configuration class per lane so the lanes edit disjoint files:
 *
 * <ul>
 *     <li>{@link FightsModuleFileConfig} - KERNEL registration of {@code fight/fight_rules.yml},
 *     {@code fight/fight_messages.yml} and {@code fight/arenas.yml}.</li>
 *     <li>{@link FightsModuleConfig} - {@link FightRules}, the {@code /glw gang fight} contribution.</li>
 *     <li>{@link FightsArenaConfig} (arenas), {@link FightsFlowConfig} (fight flow), {@link FightsEconomyConfig}
 *     (bets and settlement).</li>
 *     <li>{@code fights.command.arena} - the {@code /glw arena} command.</li>
 * </ul>
 */
@CustomLog
public final class FightsModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.fights.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.fights.command.arena";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.fights.database";

	private DependencyContainer container;

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(FightsModuleFileConfig.class)
		         .configuration(FightsModuleConfig.class)
		         .configuration(FightsArenaConfig.class)
		         .configuration(FightsFlowConfig.class)
		         .configuration(FightsEconomyConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		this.container = context.container();

		log.info("Fights module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		if (container != null && container.hasInstance(FightManager.class)) {
			container.getInstance(FightManager.class).abortAll("disable");
		}

		log.debug("Fights module disabled");
	}
}
