package org.luckyraven.gangland.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.database.MailRepository;
import org.luckyraven.gangland.mail.listener.MailJoinListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mail module's declaration to the host (0.8.2 module split, mail pilot): exactly one configuration class, its
 * listener and repository packages, and no top-level command package — the invite and ally commands are
 * {@code CommandContribution} beans attached under the core's {@code /glw gang}.
 */
@DisplayName("MailModule")
class MailModuleTest {

	@Test
	@DisplayName("configure registers MailModuleConfig plus the listener and repository packages only")
	void configure_declaresConfigAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new MailModule().configure(registrations);

		assertEquals(List.of(MailModuleConfig.class), registrations.configurations());
		assertEquals(List.of(MailModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(MailModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
		assertTrue(registrations.commandPackages().isEmpty(), "mail ships no top-level /glw command");
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(MailModule.LISTENER_PACKAGE, MailJoinListener.class.getPackageName());
		assertEquals(MailModule.REPOSITORY_PACKAGE, MailRepository.class.getPackageName());
	}
}
