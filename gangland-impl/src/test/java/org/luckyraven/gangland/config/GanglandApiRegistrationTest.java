package org.luckyraven.gangland.config;

import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.GanglandApiImpl;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * WS6 G1. Mirrors {@link WiringConfigTest}'s {@code ganglandPlaceholder} pin, for the new facade bean: entirely
 * new this gate, so "red by non-existence" is the W48-accepted evidence (this class could not compile before
 * {@link WiringConfig#ganglandApi} existed). Pins that the bean publishes {@link GanglandApiImpl} as a
 * {@link GanglandApi} service, owned by the plugin, via the same construct-register-log-return idiom
 * {@code ganglandPlaceholder} already established — the mechanism an external plugin resolves the facade through
 * ({@code Bukkit.getServicesManager().getRegistration(GanglandApi.class)}, never cached).
 */
@DisplayName("WiringConfig.ganglandApi — publishes the GanglandApi facade for external consumers")
class GanglandApiRegistrationTest {

	@Test
	@DisplayName("registers GanglandApiImpl as a GanglandApi service, owned by the plugin")
	void ganglandApi_registersGanglandApiService() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Gangland     gangland = mock(Gangland.class);
			WiringConfig config   = new WiringConfig(gangland);

			UserLookupContract     users     = mock(UserLookupContract.class);
			GangMembership         gangs     = new GangMembership();
			WaypointLookupContract waypoints = mock(WaypointLookupContract.class);
			BankTiers              bankTiers = mock(BankTiers.class);

			GanglandApiImpl api = config.ganglandApi(users, gangs, waypoints, bankTiers);

			assertNotNull(api);
			verify(bukkit.servicesManager()).register(eq(GanglandApi.class), any(GanglandApi.class), eq(gangland),
			                                          eq(ServicePriority.Normal));
		}
	}

}
