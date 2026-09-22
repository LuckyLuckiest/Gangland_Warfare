package org.luckyraven.gangland.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.keystone.placeholder.PlaceholderProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * WS1 G3 fix round 1 (Opus review I4, orchestrator ruling W36). Plaque resolves a Keystone
 * {@code org.luckyraven.keystone.placeholder.PlaceholderProvider} from the {@code ServicesManager}
 * ({@code Plaque}'s {@code PapiText.java:30-35}, lazy, uncached) as its second placeholder path, behind real
 * PlaceholderAPI, for when PAPI itself has no answer. Before this fix Gangland published only {@code ItemVocabulary}
 * on the {@code ServicesManager} ({@code GanglandContext.java:204-215}) — nothing ever registered a
 * {@code PlaceholderProvider}, so Plaque's non-PAPI branch was dead code. Pins that
 * {@link WiringConfig#ganglandPlaceholder} now publishes one, mirroring the {@code ItemVocabulary} publication
 * idiom Bartizan's {@code ItemConfig.bartizanItemVocabulary} already establishes (construct-then-register-then-log,
 * same {@code @Bean} method).
 */
@DisplayName("WiringConfig.ganglandPlaceholder — publishes a Keystone PlaceholderProvider for external consumers "
             + "(Plaque)")
class WiringConfigTest {

	@Test
	@DisplayName("registers GanglandPlaceholder.asProvider() as a PlaceholderProvider service, owned by the plugin")
	void ganglandPlaceholder_registersPlaceholderProviderService() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Gangland      gangland = mock(Gangland.class);
			WiringConfig  config   = new WiringConfig(gangland);

			@SuppressWarnings("unchecked")
			UserManager<Player> userManager        = mock(UserManager.class);
			MemberManager       memberManager       = mock(MemberManager.class);
			GangManager         gangManager         = mock(GangManager.class);
			UniqueItemAddon     uniqueItemAddon     = mock(UniqueItemAddon.class);
			BankTiers           bankTiers           = mock(BankTiers.class);
			PlaceholderService  placeholderService  = mock(PlaceholderService.class);

			GanglandPlaceholder placeholder = config.ganglandPlaceholder(userManager, memberManager, gangManager,
			                                                             uniqueItemAddon, bankTiers,
			                                                             placeholderService);

			assertNotNull(placeholder);
			verify(bukkit.servicesManager()).register(eq(PlaceholderProvider.class), any(PlaceholderProvider.class),
			                                          eq(gangland), eq(ServicePriority.Normal));
		}
	}
}
