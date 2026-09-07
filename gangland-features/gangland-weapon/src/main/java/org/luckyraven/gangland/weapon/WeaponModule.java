package org.luckyraven.gangland.weapon;

import lombok.CustomLog;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the weapon module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link WeaponFileConfig} and {@link WeaponModuleConfig} into its phased bean
 * pipeline.
 */
@CustomLog
public final class WeaponModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.weapon.listener";
	public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.weapon.command";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.weapon.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(WeaponFileConfig.class)
		         .configuration(WeaponModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Weapon module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Weapon module disabled");
	}
}
