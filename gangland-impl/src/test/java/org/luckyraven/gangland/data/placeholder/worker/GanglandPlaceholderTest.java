package org.luckyraven.gangland.data.placeholder.worker;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.placeholder.replacer.Replacer;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * gi=80: {@link GanglandPlaceholder#onRequest}'s unresolved-token fallback used to render the literal {@code "NA"}
 * for ANY parameter no branch recognized - not just Gangland-owned ones. Because {@code PlaceholderHandler}'s
 * replacer scans the whole text for every {@code %token%} regardless of prefix, a foreign or typo'd token PAPI left
 * unresolved (e.g. {@code %foo_unknown%}) still reached here and got masked as "NA" instead of staying visible.
 *
 * <p>The documented exception (S4): {@code gang_*} (and the member-touching {@code user_*} family) is answered by
 * {@code GangPlaceholderContribution} when the gang module is installed, and intentionally renders "NA" when it
 * isn't - that UX must survive the fix.
 */
@DisplayName("GanglandPlaceholder — unresolved-token fallback (gi=80)")
class GanglandPlaceholderTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initSettings() {
		SettingsFixture.initializeMinimal(tempDir);
	}

	@SuppressWarnings("unchecked")
	private GanglandPlaceholder placeholder() {
		return new GanglandPlaceholder("gangland", Replacer.Closure.PERCENT, mock(UserManager.class),
				mock(UniqueItemAddon.class), mock(BankTiers.class), mock(DependencyContainer.class),
				mock(PlaceholderService.class));
	}

	@Test
	@DisplayName("a genuinely foreign/typo'd token returns null so it stays visible as %token%")
	void onRequest_unrecognizedToken_returnsNull() {
		OfflinePlayer player = mock(OfflinePlayer.class);

		assertNull(placeholder().onRequest(player, "foo_unknown"));
	}

	@Test
	@DisplayName("a Gangland-owned prefix left unanswered (e.g. gang_* with no contribution installed) still renders NA")
	void onRequest_ganglandOwnedPrefixUnanswered_stillRendersNa() {
		OfflinePlayer player = mock(OfflinePlayer.class);

		assertEquals("NA", placeholder().onRequest(player, "gang_balance"));
	}

	@Test
	@DisplayName("player == null: a foreign/typo'd token still returns null")
	void onRequest_nullPlayer_unrecognizedToken_returnsNull() {
		assertNull(placeholder().onRequest(null, "foo_unknown"));
	}

	@Test
	@DisplayName("player == null: a module-owned setting (gang_create_fee) comes from its contribution, not the deprecated core field")
	void onRequest_nullPlayer_moduleSettingAnsweredByContribution() {
		PlaceholderContribution gang = mock(PlaceholderContribution.class);
		when(gang.resolveSetting("gang_create_fee")).thenReturn("5000");
		DependencyContainer container = mock(DependencyContainer.class);
		when(container.getAllInstances(PlaceholderContribution.class)).thenReturn(List.of(gang));

		GanglandPlaceholder placeholder = new GanglandPlaceholder("gangland", Replacer.Closure.PERCENT,
				mock(UserManager.class), mock(UniqueItemAddon.class), mock(BankTiers.class), container,
				mock(PlaceholderService.class));

		assertEquals("5000", placeholder.onRequest(null, "gang_create_fee"));
	}
}
