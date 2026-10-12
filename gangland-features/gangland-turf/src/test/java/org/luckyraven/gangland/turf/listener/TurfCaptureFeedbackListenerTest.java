package org.luckyraven.gangland.turf.listener;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enter feedback: one chat line when a gang member walks into a rival or unclaimed turf that cannot be captured yet,
 * with the same reason sentence the info card shows. Silent for gangless players, own and allied turfs, attackable
 * turfs and a turf already contesting.
 */
@DisplayName("TurfCaptureFeedbackListener - one enter line for a turf that cannot be captured yet")
class TurfCaptureFeedbackListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings>      staticSettings;
	private TurfManager                 turfs;
	private FakeGangLookup              gangs;
	private FakeUserLookup              users;
	private TurfCaptureFeedbackListener listener;
	private Player                      player;
	private Gang                        ownerGang;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");

		turfs = mock(TurfManager.class);
		gangs = new FakeGangLookup();
		users = new FakeUserLookup();

		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("14m");
		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				legacy, capture);
		CaptureEligibility eligibility = new CaptureEligibility(gangs, capture);

		listener = new TurfCaptureFeedbackListener(turfs, gangs, users, eligibility, text);

		player = CaptureFixtures.player();
		Gang viewerGang = CaptureFixtures.gang(1);
		gangs.register(viewerGang);
		users.register(player, CaptureFixtures.userInGang(1));

		ownerGang = CaptureFixtures.gang(7);
		gangs.register(ownerGang);
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	private static Turf turf(int id, String name, Integer owner, long lastCapture) {
		return new Turf(id, name, new CuboidRegion("world", 0, 0, 10, 10), owner, BigDecimal.TEN,
		                System.currentTimeMillis(), lastCapture);
	}

	private static long recentCapture() {
		return System.currentTimeMillis() - 60_000L;
	}

	@Test
	@DisplayName("a rival turf inside its cooldown sends exactly one line with the turf name and just captured")
	void rivalInCooldown_sendsOneLine() {
		Turf docks = turf(5, "Docks", 7, recentCapture());

		listener.onEnter(new TurfEnterEvent(player, docks));

		ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
		verify(player, times(1)).sendMessage(line.capture());
		assertTrue(line.getValue().contains("Docks") && line.getValue().contains("just captured"), line.getValue());
	}

	@Test
	@DisplayName("an unclaimed turf inside its cooldown sends one line too")
	void unclaimedInCooldown_sendsOneLine() {
		Turf quay = turf(6, "Quay", null, recentCapture());

		listener.onEnter(new TurfEnterEvent(player, quay));

		ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
		verify(player, times(1)).sendMessage(line.capture());
		assertTrue(line.getValue().contains("Quay") && line.getValue().contains("just captured"), line.getValue());
	}

	@Test
	@DisplayName("the player's own turf sends nothing")
	void ownTurf_silent() {
		Turf own = turf(5, "Docks", 1, recentCapture());

		listener.onEnter(new TurfEnterEvent(player, own));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("an allied turf sends nothing")
	void allyTurf_silent() {
		Turf allied = turf(5, "Docks", 7, recentCapture());
		when(ownerGang.isAlly(gangs.findById(1))).thenReturn(true);

		listener.onEnter(new TurfEnterEvent(player, allied));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("an attackable rival turf sends nothing")
	void attackableRival_silent() {
		Turf docks = turf(5, "Docks", 7, 0L);

		listener.onEnter(new TurfEnterEvent(player, docks));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a gangless player sends nothing")
	void gangless_silent() {
		Player stranger = CaptureFixtures.player();
		User<Player> gangless = mock(User.class);
		users.register(stranger, gangless);

		listener.onEnter(new TurfEnterEvent(stranger, turf(5, "Docks", 7, recentCapture())));

		verify(stranger, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a turf that is CONTESTING sends nothing")
	void contesting_silent() {
		TurfRuntimeState state = new TurfRuntimeState(5);
		state.setState(TurfState.CONTESTING);
		when(turfs.getRuntimeState(5)).thenReturn(state);

		listener.onEnter(new TurfEnterEvent(player, turf(5, "Docks", 7, recentCapture())));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("an attackable rival turf with Presence_Seconds 15 sends one count-in line with the 15 seconds (W2-R1)")
	void attackableRival_presenceOn_sendsCountInLine() throws IOException {
		CaptureRules rules = new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 24, 15, 60, 10, 4);
		when(ownerGang.getLastMemberOnlineAt()).thenReturn(System.currentTimeMillis());
		CaptureEligibility eligibility = new CaptureEligibility(gangs, CaptureFixtures.settings(180, 15, 15, 10,
		                                                                                        new int[]{25}, 90, 90),
		                                                        () -> rules, id -> 0L, id -> 99, id -> 0);
		TurfCaptureFeedbackListener counting = new TurfCaptureFeedbackListener(turfs, gangs, users, eligibility,
		                                                                       text());

		counting.onEnter(new TurfEnterEvent(player, turf(5, "Docks", 7, 0L)));

		ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
		verify(player, times(1)).sendMessage(line.capture());
		assertTrue(line.getValue().contains("Docks") && line.getValue().contains("15"), line.getValue());
	}

	@Test
	@DisplayName("an attackable rival turf with Presence_Seconds 0 stays silent (W2-R1)")
	void attackableRival_presenceOff_silent() throws IOException {
		TurfCaptureFeedbackListener silent = new TurfCaptureFeedbackListener(turfs, gangs, users,
		                                                                     new CaptureEligibility(gangs,
		                                                                                            CaptureFixtures.settings(180, 15, 15, 10, new int[]{25}, 90, 90)),
		                                                                     text());

		silent.onEnter(new TurfEnterEvent(player, turf(5, "Docks", 7, 0L)));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("an attacker gang at its turf cap gets the OVER_CAP reason line instead of nothing (W2-R1)")
	void attackerAtCap_sendsOverCapLine() throws IOException {
		CaptureEligibility capped = new CaptureEligibility(gangs,
		                                                   CaptureFixtures.settings(180, 15, 15, 10, new int[]{25}, 90, 90),
		                                                   () -> CaptureRules.LEGACY, id -> 0L, id -> 1, id -> 1);
		TurfCaptureFeedbackListener listenerAtCap = new TurfCaptureFeedbackListener(turfs, gangs, users, capped,
		                                                                            text());

		listenerAtCap.onEnter(new TurfEnterEvent(player, turf(5, "Docks", 7, 0L)));

		ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
		verify(player, times(1)).sendMessage(line.capture());
		assertTrue(line.getValue().contains("turf cap"), line.getValue());
	}

	private TurfMessages text() throws IOException {
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("14m");
		return new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				legacy, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
	}
}
