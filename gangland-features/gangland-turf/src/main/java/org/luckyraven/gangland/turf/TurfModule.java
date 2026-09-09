package org.luckyraven.gangland.turf;

import lombok.CustomLog;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Entry point of the turf module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link TurfModuleFileConfig} and {@link TurfModuleConfig} into its phased bean
 * pipeline.
 *
 * <ul>
 *     <li>{@link TurfModuleFileConfig} - the KERNEL-phase registration of {@code turf/turf_powerups.yml} and
 *     {@code turf/turf_npcs.yml} (moved from cops-n-crooks in group I, T-I5).</li>
 *     <li>{@link TurfModuleConfig} - the 29 CONFIG-phase beans: the original capture/income/repository beans moved
 *     verbatim out of the core's {@code TurfConfig}, plus the nine turf-NPC beans folded in from the deleted
 *     {@code TurfNpcsModuleConfig} (group I, T-I3). The {@code TurfNpcContracts} holder is gone (T-I4) —
 *     {@link org.luckyraven.gangland.turf.listener.powerups.GarrisonDeployListener} injects
 *     {@code TurfDefenderDeployer}/{@code TurfPowerupManager} directly instead.</li>
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

		// T-I6: since group I, turf owns the turf-NPC code (Quartermaster + garrison defenders) and its
		// Citizens-backed spawns. The actual guards live at the spawn choke points (TurfPowerupManager#spawn,
		// TurfDefenderDeployer#deploy — both check NpcSupport.available() and no-op) so a missing Citizens never
		// crashes turf capture; this is the one-time, human-readable report of that same condition (smoke row D7).
		if (!NpcSupport.available()) {
			Diagnostics.active()
			           .report(Fault.dependency(NpcSupport.FAULT_CITIZENS_MISSING,
			                                    "Citizens is not installed or not enabled — turf-NPC (Quartermaster "
			                                    + "and garrison defender) spawns will not happen; turf capture "
			                                    + "itself still works.")
			                        .build());
		}
	}

	@Override
	public void onDisabled() {
		log.debug("Turf module disabled");
	}
}
