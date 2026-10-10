package org.luckyraven.gangland.turf.listener;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfDisplayContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enter subtitles per relation (PLAN P2.4): own, ally, rival, unclaimed and gangless. The contesting branches are not
 * covered here; they read messages.format keys unchanged.
 */
@DisplayName("TurfActionBarListener - enter subtitle per relation")
class TurfActionBarListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings>      settings;
	private MockedStatic<ActionBarManager> bar;
	private TurfManager                 turfs;
	private FakeGangLookup              gangs;
	private UserLookupContract          users;
	private CaptureEligibility          eligibility;
	private TurfMessages                text;
	private TurfActionBarListener       listener;

	/** Returns the key for every format() call, so the contesting branches stay visible in an assertion. */
	private static TurfMessageContract keyEchoing() {
		return new TurfMessageContract() {
			@Override
			public String format(String key, Object... replacements) {
				return key;
			}

			@Override
			public void send(org.bukkit.command.CommandSender recipient, String key, Object... replacements) {
			}

			@Override
			public String formatDuration(long seconds) {
				return seconds + "s";
			}
		};
	}

	@BeforeEach
	void setUp() throws IOException {
		settings = TurfTextFixture.stubMoneySymbol();
		bar      = mockStatic(ActionBarManager.class);
		turfs    = mock(TurfManager.class);
		gangs    = new FakeGangLookup();
		users    = mock(UserLookupContract.class);
		text     = TurfTextFixture.messages(tempDir, keyEchoing());

		eligibility = mock(CaptureEligibility.class);
		when(eligibility.evaluate(any(Turf.class), anyLong()))
				.thenReturn(new CaptureEligibility.Verdict(Reason.ATTACKABLE, 0L));

		TurfDisplayContract display = () -> true;
		listener = new TurfActionBarListener(turfs, gangs, keyEchoing(), display, users, eligibility, text);
	}

	@AfterEach
	void tearDown() {
		bar.close();
		settings.close();
	}

	private Gang gang(int id, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getDisplayNameString()).thenReturn(name);
		gangs.register(gang);
		return gang;
	}

	private String enterSubtitle(Turf turf, Integer viewerGangId) {
		Player player = mock(Player.class);
		if (viewerGangId == null) {
			when(users.findByPlayer(player)).thenReturn(null);
		} else {
			when(users.findByPlayer(player)).thenReturn(CaptureFixtures.userInGang(viewerGangId));
		}
		listener.onEnter(new TurfEnterEvent(player, turf));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), anyInt(), anyInt(), anyInt());
		return subtitle.getValue();
	}

	private static Turf turf(Integer ownerId) {
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), ownerId, BigDecimal.ONE, 0L, 0L);
	}

	@Test
	@DisplayName("own turf: 'Your turf'")
	void subtitle_own() {
		gang(1, "Mine");

		String subtitle = enterSubtitle(turf(1), 1);

		assertTrue(subtitle.contains("Your turf"), subtitle);
	}

	@Test
	@DisplayName("allied turf: names the gang and says allied")
	void subtitle_ally() {
		Gang mine = gang(1, "Mine");
		Gang ally = gang(2, "Rats");
		when(ally.isAlly(mine)).thenReturn(true);

		String subtitle = enterSubtitle(turf(2), 1);

		assertTrue(subtitle.contains("Rats turf - allied"), subtitle);
	}

	@Test
	@DisplayName("rival turf: names the gang and carries the one reason sentence")
	void subtitle_rival_containsReason() {
		gang(1, "Mine");
		gang(2, "Rats");

		String subtitle = enterSubtitle(turf(2), 1);

		assertTrue(subtitle.contains("Rats turf"), subtitle);
		assertTrue(subtitle.contains("attackable"), subtitle);
	}

	@Test
	@DisplayName("unclaimed turf: says Unclaimed and carries the reason sentence")
	void subtitle_unclaimed_containsReason() {
		gang(1, "Mine");

		String subtitle = enterSubtitle(turf(null), 1);

		assertTrue(subtitle.contains("Unclaimed"), subtitle);
		assertTrue(subtitle.contains("attackable"), subtitle);
	}

	@Test
	@DisplayName("a gangless viewer is told to join a gang")
	void subtitle_gangless() {
		gang(2, "Rats");

		String subtitle = enterSubtitle(turf(2), null);

		assertTrue(subtitle.contains("Join a gang to capture turf"), subtitle);
	}
}
