package org.luckyraven.gangland.healthbars;

import lombok.CustomLog;
import org.luckyraven.gangland.healthbars.config.HealthBarsFileConfig;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the health-bar module ({@code module.yml} {@code Main}): a bar line over Gangland's transient Citizens
 * NPCs while they are hurt. {@code Plugins: [Citizens]} skips the module on a server without Citizens.
 *
 * <ul>
 *     <li>{@link HealthBarsFileConfig} - the FILE-phase {@code healthBarSettings} bean ({@code healthbars.yml}).</li>
 *     <li>{@code healthbars.listener} - the damage/regain listener that draws the bar.</li>
 * </ul>
 */
@CustomLog
public final class HealthBarsModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE = "org.luckyraven.gangland.healthbars.listener";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(HealthBarsFileConfig.class).listenerPackage(LISTENER_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Health bar module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Health bar module disabled");
	}
}
