package org.luckyraven.gangland.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the mail module's own YAML defaults ({@code mail/mail_rules.yml},
 * {@code mail/mail_messages.yml}, {@code mail/mail_messages_es.yml}). The five-argument {@link FileHandler} copies each
 * bundled default out of the module jar through the module classloader. Same shape as {@code TurfModuleFileConfig}.
 */
@Configuration(phase = Phase.KERNEL)
public final class MailModuleFileConfig {

	private final JavaPlugin plugin;

	public MailModuleFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public MailModuleFiles mailModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		FileHandler rules = new FileHandler(plugin, "mail_rules", "mail", ".yml", moduleLoader.classLoader());
		fileManager.addFile(rules, true);

		FileHandler messages = new FileHandler(plugin, "mail_messages", "mail", ".yml", moduleLoader.classLoader());
		fileManager.addFile(messages, true);

		FileHandler messagesEs = new FileHandler(plugin, "mail_messages_es", "mail", ".yml",
		                                         moduleLoader.classLoader());
		fileManager.addFile(messagesEs, true);

		return new MailModuleFiles(rules, messages, messagesEs);
	}

}
