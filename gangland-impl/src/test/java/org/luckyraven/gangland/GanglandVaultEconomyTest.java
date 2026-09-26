package org.luckyraven.gangland;

import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Vault economy hook used to log "Linked Vault economy" even when Vault had no economy provider registered: the
 * callback returned silently and {@code Dependency.validate} logged the link unconditionally, while Gangland kept
 * running on its internal balances. {@link Gangland#linkVaultEconomy} now reports whether it linked, so
 * {@code validate} logs "Linked" only on a real link.
 */
@DisplayName("Gangland.linkVaultEconomy - logs only what actually happened")
class GanglandVaultEconomyTest {

	@AfterEach
	void resetVaultEconomy() {
		EconomyHandler.setVaultEconomy(null);
	}

	@Test
	@DisplayName("Vault without an economy provider links nothing and says Gangland keeps its internal balances")
	void linkVaultEconomy_noProvider_reportsInternalBalances() {
		ServicesManager services = mock(ServicesManager.class);
		List<String>    logs     = new ArrayList<>();
		boolean         linked   = captureInfoLogs(logs, () -> Gangland.linkVaultEconomy(services));

		assertFalse(linked, "nothing was linked, so validate must not log 'Linked Vault economy'");
		assertNull(EconomyHandler.getVaultEconomy());
		assertTrue(logs.stream().anyMatch(line -> line.contains("no economy provider")), "got " + logs);
	}

	@Test
	@DisplayName("a registered economy provider is linked")
	@SuppressWarnings("unchecked")
	void linkVaultEconomy_provider_linksIt() {
		Economy                            economy      = mock(Economy.class);
		RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
		ServicesManager                    services     = mock(ServicesManager.class);
		when(registration.getProvider()).thenReturn(economy);
		when(services.getRegistration(Economy.class)).thenReturn(registration);

		assertTrue(Gangland.linkVaultEconomy(services));
		assertSame(economy, EconomyHandler.getVaultEconomy());
	}

	/** Same technique as {@code SettingsTest.captureWarnLogs}, at INFO, on {@code Gangland}'s own logger. */
	private static boolean captureInfoLogs(List<String> captured, java.util.function.BooleanSupplier action) {
		Logger logger        = (Logger) org.luckyraven.keystone.logging.Logger.getLogger(Gangland.class);
		Level  originalLevel = logger.getLevel();
		AbstractAppender appender = new AbstractAppender("vault-economy-test-capture", null, null, false,
		                                                 Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				captured.add(event.getMessage().getFormattedMessage());
			}
		};
		appender.start();
		logger.addAppender(appender);
		Configurator.setLevel(logger, Level.INFO);
		try {
			return action.getAsBoolean();
		} finally {
			Configurator.setLevel(logger, originalLevel);
			logger.removeAppender(appender);
			appender.stop();
		}
	}
}
