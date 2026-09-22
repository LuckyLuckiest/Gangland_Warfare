package org.luckyraven.gangland.lootchest;

import lombok.CustomLog;
import org.luckyraven.gangland.lootchest.config.LootChestFileConfig;
import org.luckyraven.gangland.lootchest.config.LootChestModuleConfig;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the loot chest module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link LootChestFileConfig} and {@link LootChestModuleConfig} into its phased bean
 * pipeline.
 *
 * <ul>
 *     <li>{@link LootChestFileConfig} - the KERNEL-phase {@code loot_chests}/{@code tiers} YAML FileHandlers.</li>
 *     <li>{@link LootChestModuleConfig} - the hologram bean (now {@code keystone-hologram}), {@code LootChestManager},
 *     its {@code LootChestLoader} and the {@code NbtTagCatalog} tag registration.</li>
 *     <li>{@code lootchest.listener} - the chest-interaction, wand and reward-on-open listeners.</li>
 *     <li>{@code lootchest.command} - the root {@code /glw lootchest} command and its sub-arguments.</li>
 *     <li>{@code lootchest.database} - the {@code LootChestRepository} and its table, scanned through the module
 *     loader.</li>
 * </ul>
 */
@CustomLog
public final class LootChestModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.lootchest.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.lootchest.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.lootchest.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(LootChestFileConfig.class)
		         .configuration(LootChestModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Loot chest module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Loot chest module disabled");
	}
}
