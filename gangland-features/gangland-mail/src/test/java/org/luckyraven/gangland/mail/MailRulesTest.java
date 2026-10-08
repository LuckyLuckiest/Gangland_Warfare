package org.luckyraven.gangland.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@code mail/mail_rules.yml}: the shipped default is 72 hours, a configured value is read, a negative value is
 * clamped to 0 (never expires), and a file without the key falls back to 72 (GR-23).
 */
@DisplayName("MailRules - Invite_Offline_Expiry_Hours (GR-23)")
class MailRulesTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("the bundled mail_rules.yml ships 72 hours")
	void bundled_shipsSeventyTwoHours() {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, "mail_rules", "mail", ".yml", MailRulesTest.class.getClassLoader()),
		                    true);

		assertEquals(72, new MailRules(fileManager).getInviteOfflineExpiryHours());
	}

	@Test
	@DisplayName("a configured value is read")
	void configuredValue_isRead() throws IOException {
		assertEquals(24, rulesFrom("Invite_Offline_Expiry_Hours: 24\n").getInviteOfflineExpiryHours());
	}

	@Test
	@DisplayName("a negative value is clamped to 0 (the old never-expires behaviour)")
	void negativeValue_clampsToZero() throws IOException {
		assertEquals(0, rulesFrom("Invite_Offline_Expiry_Hours: -5\n").getInviteOfflineExpiryHours());
	}

	@Test
	@DisplayName("a file without the key falls back to 72")
	void missingKey_fallsBackToSeventyTwo() throws IOException {
		assertEquals(72, rulesFrom("Other_Key: 1\n").getInviteOfflineExpiryHours());
	}

	private MailRules rulesFrom(String yaml) throws IOException {
		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Path       file   = tempDir.resolve("mail_rules.yml");
		Files.writeString(file, yaml);

		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new MailRules(fileManager);
	}

}
