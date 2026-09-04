package org.luckyraven.gangland.mail;

import lombok.CustomLog;
import org.luckyraven.keystone.module.KeystoneModule;
import org.luckyraven.keystone.module.ModuleContext;
import org.luckyraven.keystone.module.ModuleRegistrar;

/**
 * Entry point of the mail module ({@code module.yml} {@code Main}). Declares what the module contributes; the
 * host runs the scans and folds {@link MailModuleConfig} into its phased bean pipeline.
 *
 * <ul>
 *     <li>{@link MailModuleConfig} - the {@code MailManager}, its repository contract, the expiry sweep and the
 *     two {@code CommandContribution} beans that attach the invite and ally sub-arguments to {@code /glw gang}.</li>
 *     <li>{@code mail.listener} - join/quit listeners surfacing and pausing pending mail.</li>
 *     <li>{@code mail.database} - the {@code MailRepository} and its table, scanned through the module loader.</li>
 * </ul>
 *
 * No command package: the module owns no top-level {@code /glw} command, only sub-arguments under {@code gang}.
 */
@CustomLog
public final class MailModule implements KeystoneModule {

	public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.mail.listener";
	public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.mail.database";

	@Override
	public void configure(ModuleRegistrar registrar) {
		registrar.configuration(MailModuleConfig.class)
		         .listenerPackage(LISTENER_PACKAGE)
		         .repositoryPackage(REPOSITORY_PACKAGE);
	}

	@Override
	public void onEnabled(ModuleContext context) {
		log.info("Mail module {} enabled", context.module().descriptor().version());
	}

	@Override
	public void onDisabled() {
		log.debug("Mail module disabled");
	}
}
