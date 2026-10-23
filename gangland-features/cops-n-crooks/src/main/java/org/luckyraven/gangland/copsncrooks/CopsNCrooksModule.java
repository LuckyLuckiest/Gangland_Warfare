package org.luckyraven.gangland.copsncrooks;

import lombok.CustomLog;
import org.luckyraven.gangland.copsncrooks.config.BreachModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.BagModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ChaseModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksFileConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.config.CrimeSourcesModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.WitnessModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.DisguiseModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.PatrolModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.TrackerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ScannerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.SightModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.OverwatchModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ScannerMapModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.LookoutModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.EvasionModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.GetawayModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.HeatModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.MostWantedModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.PursuitReportModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.RegistryModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.RescueModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.StandoffModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.SurrenderModuleConfig;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Entry point of the cops-n-crooks module ({@code module.yml} {@code Main}). Declares what the module contributes;
 * the host runs the scans and folds the module's configuration classes into its phased bean pipeline.
 *
 * <p>Three configuration classes (down from six) after D5 (civilians, group H) + D6 (npc-shops, group J):
 * {@code BankerModuleConfig}/{@code TraderModuleConfig} moved to {@code gangland-npc-shops} and
 * {@code TurfNpcsModuleConfig} was folded into {@code gangland-turf}'s {@code TurfModuleConfig} (group I).
 * 0.15.0 adds {@code ChaseModuleConfig}, {@code HeatModuleConfig} and {@code EvasionModuleConfig} (the chase);
 * 0.16.0 adds {@code RegistryModuleConfig} (stations, admin regions, setup points).
 * 0.20.0 adds the nine lane seam configurations (crime sources, witness, disguise, patrol, tracker, scanner,
 * sight, overwatch, scanner map) as empty skeletons, one per lane file.
 */
@CustomLog
public final class CopsNCrooksModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.copsncrooks.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.copsncrooks.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.copsncrooks.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(CopsNCrooksYamlConfig.class)
		         .configuration(CopsNCrooksFileConfig.class)
		         .configuration(CopsNCrooksModuleConfig.class)
		         .configuration(ChaseModuleConfig.class)
		         .configuration(HeatModuleConfig.class)
		         .configuration(EvasionModuleConfig.class)
		         .configuration(RegistryModuleConfig.class)
		         // 0.21.0
		         .configuration(SurrenderModuleConfig.class)
		         .configuration(RescueModuleConfig.class)
		         .configuration(StandoffModuleConfig.class)
		         .configuration(BreachModuleConfig.class)
		         .configuration(PursuitReportModuleConfig.class)
		         // 0.21.0 wave 2
		         .configuration(GetawayModuleConfig.class)
		         .configuration(MostWantedModuleConfig.class)
		         .configuration(CrimeSourcesModuleConfig.class)
		         .configuration(WitnessModuleConfig.class)
		         .configuration(DisguiseModuleConfig.class)
		         .configuration(PatrolModuleConfig.class)
		         .configuration(TrackerModuleConfig.class)
		         .configuration(ScannerModuleConfig.class)
		         .configuration(SightModuleConfig.class)
		         .configuration(OverwatchModuleConfig.class)
		         .configuration(ScannerMapModuleConfig.class)
		         .configuration(BagModuleConfig.class)
		         .configuration(LookoutModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Cops-n-crooks module {} enabled", context.module().descriptor().version());

		// T-KR1: the actual guard lives at the spawn choke point (CopNpcFactory#createCop, which no-ops when
		// Citizens is absent) — this is the one-time, human-readable report of that same condition, matching
		// CiviliansModule/TurfModule's shape. Cops have no module-level spawn timer to skip here: unlike
		// civilians/turf, cop AI/spawn BukkitTasks are started per-player from CopListener's wanted-event
		// handlers (CopManager#onWantedStart), never eagerly at module enable time, so there is no
		// CopSpawnManager/CopManager "start" call site to gate.
		if (!NpcSupport.available()) {
			Diagnostics.active()
			           .report(Fault.dependency(NpcSupport.FAULT_CITIZENS_MISSING,
			                                    "Citizens is not installed or not enabled — cop NPCs will not spawn.")
			                        .build());
		}
	}

	@Override
	public void onDisabled() {
		log.debug("Cops-n-crooks module disabled");
	}
}
