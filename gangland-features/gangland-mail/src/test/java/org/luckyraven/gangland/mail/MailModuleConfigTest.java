package org.luckyraven.gangland.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Pins T-194: the {@code mailManager} bean must not call {@link MailManager#initialize()} itself; the bean factory's
 * convention pass runs it once. A second call re-read the table and re-registered the data supplier.
 */
@DisplayName("MailModuleConfig - MailManager is not self-initialised (T-194)")
class MailModuleConfigTest {

	@Test
	@DisplayName("the mailManager bean method leaves initialize() to the convention pass")
	void mailManager_doesNotSelfInitialize() {
		FakeMailRepositoryContract repository = new FakeMailRepositoryContract();

		MailManager manager = new MailModuleConfig(mock(JavaPlugin.class)).mailManager(repository);

		assertNotNull(manager);
		assertFalse(repository.hasDataSupplier(), "initialize() ran inside the bean method (the second load)");
	}

}
