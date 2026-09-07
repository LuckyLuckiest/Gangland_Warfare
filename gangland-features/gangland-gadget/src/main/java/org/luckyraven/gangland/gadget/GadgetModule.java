package org.luckyraven.gangland.gadget;

import lombok.CustomLog;
import org.luckyraven.gangland.gadget.config.GadgetFileConfig;
import org.luckyraven.gangland.gadget.config.GadgetModuleConfig;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the gadget module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link GadgetFileConfig} and {@link GadgetModuleConfig} into its phased bean
 * pipeline.
 *
 * <ul>
 *     <li>{@link GadgetFileConfig} - the FILE-phase {@code carAddon} and {@code gadgetPhysicsConfig} beans.</li>
 *     <li>{@link GadgetModuleConfig} - the {@code CarService}, {@code JetpackService} and the car contract
 *     implementations.</li>
 *     <li>{@code gadget.listener} - the car and jetpack listeners.</li>
 *     <li>{@code gadget.command} - the {@code /glw car} sub-commands.</li>
 *     <li>{@code gadget.database} - the {@code ParkedCarRepository} and its table, scanned through the module
 *     loader.</li>
 * </ul>
 */
@CustomLog
public final class GadgetModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.gadget.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.gadget.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.gadget.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(GadgetFileConfig.class)
		         .configuration(GadgetModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Gadget module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Gadget module disabled");
	}
}
