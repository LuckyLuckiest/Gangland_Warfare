package org.luckyraven.gangland.npcshops;

import lombok.CustomLog;
import org.luckyraven.gangland.npcshops.config.BankerModuleConfig;
import org.luckyraven.gangland.npcshops.config.NpcShopsModuleConfig;
import org.luckyraven.gangland.npcshops.config.NpcShopsYamlConfig;
import org.luckyraven.gangland.npcshops.config.TraderModuleConfig;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Entry point of the npc-shops module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds this module's configuration classes into its phased bean pipeline.
 *
 * <ul>
 *     <li>{@link NpcShopsYamlConfig} — the KERNEL-phase registration of {@code npc/trader_traits.yml} and
 *     {@code npc/bank_tiers.yml} (T-J5).</li>
 *     <li>{@link TraderModuleConfig} / {@link BankerModuleConfig} — the CONFIG-phase trader/banker bean wiring,
 *     moved verbatim from cops-n-crooks (T-J2/T-J3, group J).</li>
 *     <li>{@link NpcShopsModuleConfig} — {@code bankMenuContribution} and the {@code BankTiers} seam install, both
 *     moved out of cops-n-crooks' {@code CopsNCrooksModuleConfig} (T-J3).</li>
 *     <li>{@code npcshops.listener.{trader,banker}} — {@code @ListenerHandler} classes.</li>
 *     <li>{@code npcshops.command.{trader,banker,bank}} — the {@code /glw trader}/{@code banker}/
 *     {@code bank menu} command tree.</li>
 *     <li>{@code npcshops.database} — the {@code TraderData}/{@code BankerData} repositories and tables (table
 *     names {@code trader}/{@code banker} unchanged, no migration).</li>
 * </ul>
 *
 * <p>{@code TraderNpc}/{@code BankerNpc} hold a raw Citizens {@code NPC} directly (not {@code AbstractNpc}
 * subclasses), so this module needs {@link NpcSupport} for the runtime gate but not {@code keystone-npc}
 * inheritance — no {@code Depends:}/{@code Plugins:} in {@code module.yml}.
 */
@CustomLog
public final class NpcShopsModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.npcshops.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.npcshops.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.npcshops.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(NpcShopsYamlConfig.class)
		         .configuration(TraderModuleConfig.class)
		         .configuration(BankerModuleConfig.class)
		         .configuration(NpcShopsModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("NPC Shops module {} enabled", context.module().descriptor().version());

		// T-J4: the actual spawn-skip lives at the two real Citizens choke points, TraderManager#spawn and
		// BankerManager#spawn (mirrors T-H4/T-I6's pattern) — this is the one-time, human-readable report of that
		// same condition.
		if (!NpcSupport.available()) {
			Diagnostics.active()
			           .report(Fault.dependency(NpcSupport.FAULT_CITIZENS_MISSING,
			                                    "Citizens is not installed or not enabled — traders and bankers "
			                                    + "will not spawn.")
			                        .build());
		}
	}

	@Override
	public void onDisabled() {
		log.debug("NPC Shops module disabled");
	}
}
