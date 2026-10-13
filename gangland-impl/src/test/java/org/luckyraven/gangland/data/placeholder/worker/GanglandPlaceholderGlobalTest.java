package org.luckyraven.gangland.data.placeholder.worker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.placeholder.replacer.Replacer;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * api 2.6 {@code PlaceholderContribution#resolveGlobal}: a playerless token that is neither {@code user_} nor
 * {@code bank_} (a gang board row) is answered by a contribution; the player-bound families never reach it.
 */
@DisplayName("GanglandPlaceholder - playerless resolveGlobal")
class GanglandPlaceholderGlobalTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initSettings() {
		SettingsFixture.initializeMinimal(tempDir);
	}

	private static GanglandPlaceholder with(PlaceholderContribution contribution) {
		DependencyContainer container = mock(DependencyContainer.class);
		when(container.getAllInstances(PlaceholderContribution.class)).thenReturn(List.of(contribution));

		return new GanglandPlaceholder("gangland", Replacer.Closure.PERCENT, mock(UserManager.class),
		                               mock(UniqueItemAddon.class), mock(BankTiers.class), container,
		                               mock(PlaceholderService.class), new ContactDesk(() -> 0L));
	}

	@Test
	@DisplayName("nullPlayer_boardToken_reachesResolveGlobal")
	void nullPlayer_boardToken_reachesResolveGlobal() {
		PlaceholderContribution gang = mock(PlaceholderContribution.class);
		when(gang.resolveGlobal("gang_board_kdr_1_name")).thenReturn("Bloods");

		assertEquals("Bloods", with(gang).onRequest(null, "gang_board_kdr_1_name"));
	}

	@Test
	@DisplayName("userToken_neverReachesIt")
	void userToken_neverReachesIt() {
		PlaceholderContribution gang = mock(PlaceholderContribution.class);
		when(gang.resolveGlobal("user_balance")).thenReturn("leak");
		when(gang.resolveGlobal("bank_balance")).thenReturn("leak");

		GanglandPlaceholder placeholder = with(gang);

		assertEquals("NA", placeholder.onRequest(null, "user_balance"));
		assertEquals("NA", placeholder.onRequest(null, "bank_balance"));
		verify(gang, never()).resolveGlobal("user_balance");
		verify(gang, never()).resolveGlobal("bank_balance");
	}

	@Test
	@DisplayName("foreignToken_unanswered_staysNull")
	void foreignToken_unanswered_staysNull() {
		assertNull(with(mock(PlaceholderContribution.class)).onRequest(null, "foo_unknown"));
	}
}
