package org.luckyraven.gangland.turf;

import lombok.CustomLog;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the turf module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link TurfModuleFileConfig} and {@link TurfModuleConfig} into its phased bean
 * pipeline.
 *
 * <ul>
 *     <li>{@link TurfModuleFileConfig} - the KERNEL-phase registration of {@code turf/turf_powerups.yml}.</li>
 *     <li>{@link TurfModuleConfig} - the 21 CONFIG-phase beans (repositories, managers, capture/income tasks and
 *     the {@code TurfNpcContracts} holder) moved verbatim out of the core's {@code TurfConfig}.</li>
 *     <li>{@code turf.listener} and {@code turf.task} - both are scanned as listener packages;
 *     {@code turf.task.GangPresenceListener} carries {@code @ListenerHandler} but has not yet been relocated
 *     under {@code turf.listener} (tracked as a follow-up, not part of this flip).</li>
 *     <li>{@code turf.command} - the {@code /glw turf} command tree.</li>
 *     <li>{@code turf.database} - the {@code Turf}/{@code ActiveTurfBuff}/{@code Garrison} repositories and their
 *     tables, scanned through the module loader.</li>
 * </ul>
 */
@CustomLog
public final class TurfModule implements KeystoneModule {

	public static final String CONFIG_PACKAGE     = "org.luckyraven.gangland.turf";        // documentation only
	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.turf.listener";
	public static final String TASK_PACKAGE       = "org.luckyraven.gangland.turf.task";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.turf.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.turf.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(TurfModuleFileConfig.class)
		         .configuration(TurfModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .listenerPackage(TASK_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Turf module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Turf module disabled");
	}
}
