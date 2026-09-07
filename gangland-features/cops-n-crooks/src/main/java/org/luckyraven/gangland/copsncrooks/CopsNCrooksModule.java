package org.luckyraven.gangland.copsncrooks;

import lombok.CustomLog;
import org.luckyraven.gangland.copsncrooks.config.BankerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksFileConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.config.TraderModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.TurfNpcsModuleConfig;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the cops-n-crooks module ({@code module.yml} {@code Main}). Declares what the module contributes;
 * the host runs the scans and folds the module's configuration classes into its phased bean pipeline.
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
		         .configuration(BankerModuleConfig.class)
		         .configuration(TraderModuleConfig.class)
		         .configuration(TurfNpcsModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .commandPackage(COMMAND_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Cops-n-crooks module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Cops-n-crooks module disabled");
	}
}
