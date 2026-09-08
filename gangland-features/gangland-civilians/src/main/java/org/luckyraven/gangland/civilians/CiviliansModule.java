package org.luckyraven.gangland.civilians;

import lombok.CustomLog;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Entry point of the civilians module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link CiviliansYamlConfig} and {@link CiviliansModuleConfig} into its phased bean
 * pipeline.
 *
 * <ul>
 *     <li>{@link CiviliansYamlConfig} — the KERNEL-phase registration of {@code npc/civilians.yml}.</li>
 *     <li>{@link CiviliansModuleConfig} — the CONFIG-phase beans: {@code CiviliansLoader}, the Keystone
 *     {@code NpcMarkManager} + {@code GanglandMarkDefaults}, {@code BartizanNpcWeapons},
 *     {@code DownedTargetFilter}, {@code GanglandCombatEligibility}, {@code CivilianNpcRegistry},
 *     {@code CivilianNpcFactory}, {@code CivilianSpawnManager}, {@code CivilianService}.</li>
 *     <li>{@code civilians.listener} — {@code @ListenerHandler} classes, including
 *     {@code listener.gang.GangAllyWeaponImpactListener} (T-H5), gated for free by this module's own
 *     {@code Plugins: [Bartizan]}.</li>
 *     <li>{@code civilians.command} — the {@code /glw civilian}/{@code civiliangroups}/{@code civilianspawner}
 *     command tree.</li>
 *     <li>{@code civilians.database} — the {@code CivilianSpawner} repository and table.</li>
 * </ul>
 */
@CustomLog
public final class CiviliansModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.civilians.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.civilians.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.civilians.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(CiviliansYamlConfig.class)
		         .configuration(CiviliansFileConfig.class)
		         .configuration(CiviliansModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Civilians module {} enabled", context.module().descriptor().version());

		// T-H4: the spawn tasks themselves are guarded in CivilianService#onInitialize (Citizens-dependent code
		// must never run when the plugin is absent, regardless of bean-lifecycle vs. module-enable ordering) —
		// this is the one-time, human-readable report of that same condition.
		if (!NpcSupport.available()) {
			Diagnostics.active()
			           .report(Fault.dependency(NpcSupport.FAULT_CITIZENS_MISSING,
			                                    "Citizens is not installed or not enabled — civilian NPCs will not spawn.")
			                        .build());
		}
	}

	@Override
	public void onDisabled() {
		log.debug("Civilians module disabled");
	}
}
