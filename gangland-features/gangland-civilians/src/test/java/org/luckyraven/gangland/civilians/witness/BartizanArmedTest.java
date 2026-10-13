package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("BartizanArmed - a fresh Bartizan lookup on every call, absent means unarmed")
class BartizanArmedTest {

	private final Player player = mock(Player.class);

	private static void bartizanEnabled(MockedStatic<Bukkit> bukkit, boolean enabled) {
		PluginManager plugins = mock(PluginManager.class);
		when(plugins.isPluginEnabled("Bartizan")).thenReturn(enabled);
		bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
	}

	@Test
	@DisplayName("Bartizan not enabled: never armed and the ServicesManager is never touched (no BartizanApi literal)")
	void bartizanNotEnabled() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bartizanEnabled(bukkit, false);

			assertFalse(new BartizanArmed().test(player));

			bukkit.verify(Bukkit::getServicesManager, never());
		}
	}

	@Test
	@DisplayName("no Bartizan service registered: never armed, no exception")
	void bartizanAbsent() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bartizanEnabled(bukkit, true);
			ServicesManager services = mock(ServicesManager.class);
			bukkit.when(Bukkit::getServicesManager).thenReturn(services);
			when(services.getRegistration(BartizanApi.class)).thenReturn(null);

			assertFalse(new BartizanArmed().test(player));
		}
	}

	@Test
	@DisplayName("Bartizan holds a weapon for the player: armed")
	void weaponHeld() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bartizanEnabled(bukkit, true);
			ServicesManager services = servicesWith(mock(Weapon.class));
			bukkit.when(Bukkit::getServicesManager).thenReturn(services);

			assertTrue(new BartizanArmed().test(player));
		}
	}

	@Test
	@DisplayName("Bartizan reports no held weapon: unarmed")
	void nothingHeld() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bartizanEnabled(bukkit, true);
			ServicesManager services = servicesWith(null);
			bukkit.when(Bukkit::getServicesManager).thenReturn(services);

			assertFalse(new BartizanArmed().test(player));
		}
	}

	@Test
	@DisplayName("the service is looked up again on every call (never cached)")
	void lookedUpEveryCall() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bartizanEnabled(bukkit, true);
			BartizanArmed armed = new BartizanArmed();

			ServicesManager armedServices = servicesWith(mock(Weapon.class));
			bukkit.when(Bukkit::getServicesManager).thenReturn(armedServices);
			assertTrue(armed.test(player));

			ServicesManager gone = mock(ServicesManager.class);
			when(gone.getRegistration(BartizanApi.class)).thenReturn(null);
			bukkit.when(Bukkit::getServicesManager).thenReturn(gone);
			assertFalse(armed.test(player));
		}
	}

	@SuppressWarnings("unchecked")
	private ServicesManager servicesWith(Weapon held) {
		BartizanApi api = mock(BartizanApi.class);
		when(api.getHeldWeapon(player)).thenReturn(held);
		RegisteredServiceProvider<BartizanApi> provider = mock(RegisteredServiceProvider.class);
		when(provider.getProvider()).thenReturn(api);
		ServicesManager services = mock(ServicesManager.class);
		when(services.getRegistration(BartizanApi.class)).thenReturn(provider);
		return services;
	}
}
