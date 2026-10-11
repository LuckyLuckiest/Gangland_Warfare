package org.luckyraven.gangland.turf.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Turf alerts that reach a gang anywhere: the start line with attacker count and side, the half-guards and last-stand
 * lines (once per contest, throttled to one per turf per player per 10 seconds), allies hearing only the start line,
 * and the outcome lines (held, failed, stalled, lost, taken) that always go out.
 */
@DisplayName("TurfCaptureNotifier - turf alerts anywhere with count, side, half guards, last stand and outcomes")
class TurfCaptureNotifierTest {

	private static final int TURF_ID = 1;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings>      staticSettings;
	private BukkitStatics               bukkit;
	private final List<Player>          online = new ArrayList<>();
	private final AtomicLong            clock  = new AtomicLong(1_000_000L);
	private TurfMessageContract         legacy;
	private FakeGangLookup              gangs;
	private FakeUserLookup              users;
	private TurfManager                 turfs;
	private Turf                        turf;
	private TurfRuntimeState            runtime;
	private Gang                        owner;
	private Gang                        challenger;
	private Gang                        ally;
	private World                       overworld;
	private World                       nether;
	private TurfCaptureNotifier         notifier;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = TurfTextFixture.stubMoneySymbol();
		bukkit = BukkitStatics.install();
		bukkit.statics().when(() -> Bukkit.getOnlinePlayers()).thenAnswer(invocation -> List.copyOf(online));

		overworld = world("world");
		nether = world("world_nether");

		legacy = mock(TurfMessageContract.class);
		TurfMessages text = TurfTextFixture.messages(tempDir, legacy);
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);

		owner = named(1, "Vipers");
		challenger = named(2, "Rats");
		ally = named(3, "Cobras");
		when(ally.isAlly(owner)).thenReturn(true);
		gangs = new FakeGangLookup();
		gangs.register(owner);
		gangs.register(challenger);
		gangs.register(ally);
		users = new FakeUserLookup();

		turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turf.getDisplayName()).thenReturn("Docks");
		when(turf.getOwnerGangId()).thenReturn(1);
		when(turf.getRegion()).thenReturn(new CuboidRegion("world", 0, 0, 100, 100));

		runtime = new TurfRuntimeState(TURF_ID);
		turfs = mock(TurfManager.class);
		when(turfs.getRuntimeState(TURF_ID)).thenReturn(runtime);
		when(turfs.get(TURF_ID)).thenReturn(turf);

		notifier = new TurfCaptureNotifier(legacy, gangs, users, settings, text, turfs);
		notifier.setClock(clock::get);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		staticSettings.close();
	}

	@Test
	@DisplayName("start reaches owners anywhere, with the attacker count and the side")
	void start_reachesOwnersAnywhere_withCountAndDirection() {
		Player ownerHere = member(1, overworld, 10, 10);
		Player ownerNether = member(1, nether, 0, 0);
		member(2, overworld, 80, 50);
		member(2, overworld, 80, 50);
		runtime.setChallengersInside(Map.of(2, 2));

		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));

		assertEquals(List.of("Docks is under attack! 2 Rats from the E"), linesOf(ownerHere));
		assertEquals(List.of("Docks is under attack! 2 Rats from the E"), linesOf(ownerNether));
	}

	@Test
	@DisplayName("start: allies get the start line only, never the later alerts")
	void start_alliesGetTheStartLineOnly() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player allyPlayer = member(3, overworld, 10, 10);
		member(2, overworld, 80, 50);
		runtime.setChallengersInside(Map.of(2, 1));

		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		assertEquals(List.of("Docks (Vipers) is under attack by Rats"), linesOf(allyPlayer));

		clock.addAndGet(20_000L);
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 3, 6));
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(1, linesOf(allyPlayer).size());
		assertEquals(3, linesOf(ownerPlayer).size());
	}

	@Test
	@DisplayName("start on an unclaimed turf is silent")
	void start_unclaimedTurf_isSilent() {
		when(turf.getOwnerGangId()).thenReturn(null);
		Player attacker = member(2, overworld, 80, 50);
		Player bystander = member(3, overworld, 10, 10);
		runtime.setChallengersInside(Map.of(2, 1));

		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));

		assertTrue(linesOf(attacker).isEmpty());
		assertTrue(linesOf(bystander).isEmpty());
	}

	@Test
	@DisplayName("half the guards: once per contest, when half are down")
	void halfGuards_onceWhenHalfAreDown() {
		Player ownerPlayer = member(1, overworld, 10, 10);

		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 4, 6));
		assertTrue(linesOf(ownerPlayer).isEmpty());

		clock.addAndGet(20_000L);
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 3, 6));
		assertEquals(List.of("Half the guards at Docks are down"), linesOf(ownerPlayer));

		clock.addAndGet(20_000L);
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 2, 6));
		assertEquals(1, linesOf(ownerPlayer).size());
	}

	@Test
	@DisplayName("last stand: once at 75 percent in a contest")
	void lastStand_onceAt75() {
		Player ownerPlayer = member(1, overworld, 10, 10);

		notifier.onCaptureProgress(progress(50.0));
		assertTrue(linesOf(ownerPlayer).isEmpty());

		notifier.onCaptureProgress(progress(75.0));
		assertEquals(List.of("Last stand at Docks - 75%"), linesOf(ownerPlayer));

		clock.addAndGet(20_000L);
		notifier.onCaptureProgress(progress(75.0));
		assertEquals(1, linesOf(ownerPlayer).size());
	}

	@Test
	@DisplayName("throttle: ten seconds per turf per player per kind - a start line never swallows the one-shot half-guards line")
	void throttle_tenSecondsPerTurfPerPlayerPerKind() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		member(2, overworld, 80, 50);
		runtime.setChallengersInside(Map.of(2, 1));

		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		clock.addAndGet(3_000L);
		// T-285: guards that fall within seconds of the start still get their alert; it is once per contest, so a
		// throttled send would have lost it for good.
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 3, 6));
		assertEquals(2, linesOf(ownerPlayer).size());
		assertEquals("Half the guards at Docks are down", linesOf(ownerPlayer).get(1));

		// the same kind is still throttled: a second start within ten seconds adds nothing
		clock.addAndGet(1_000L);
		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		assertEquals(2, linesOf(ownerPlayer).size());

		clock.addAndGet(7_000L);
		notifier.onCaptureProgress(progress(75.0));
		assertEquals(3, linesOf(ownerPlayer).size());
		assertEquals("Last stand at Docks - 75%", linesOf(ownerPlayer).get(2));
	}

	@Test
	@DisplayName("defended and abandoned tell the owners they held and the attackers their attack failed")
	void defended_tellsBothSides() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player attacker = member(2, overworld, 80, 50);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(20_000L);
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.ABANDONED, 2));

		// T-271: DEFENDED and ABANDONED share the HELD bucket, so the repeat within 15 minutes is not told again.
		assertEquals(List.of("Docks held against Rats"), linesOf(ownerPlayer));
		assertEquals(List.of("Your attack on Docks failed"), linesOf(attacker));
	}

	@Test
	@DisplayName("stalled tells both sides the attack stalled")
	void stalled_tellsBothSides() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player attacker = member(2, overworld, 80, 50);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.STALLED, 2));

		assertEquals(List.of("Docks: the attack stalled"), linesOf(ownerPlayer));
		assertEquals(List.of("Docks: the attack stalled"), linesOf(attacker));
	}

	@Test
	@DisplayName("T-271: a held turf told twice within the 15-minute quiet window is told once")
	void heldTwiceWithinQuietWindow_toldOnce() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player attacker = member(2, overworld, 80, 50);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(10 * 60_000L);
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(List.of("Docks held against Rats"), linesOf(ownerPlayer));
		assertEquals(List.of("Your attack on Docks failed"), linesOf(attacker));
	}

	@Test
	@DisplayName("T-271: a stall every ten minutes is told once per side")
	void stalledEveryTenMinutes_toldOnce() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player attacker = member(2, overworld, 80, 50);

		for (int i = 0; i < 3; i++) {
			notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.STALLED, 2));
			clock.addAndGet(10 * 60_000L);
		}

		assertEquals(List.of("Docks: the attack stalled"), linesOf(ownerPlayer));
		assertEquals(List.of("Docks: the attack stalled"), linesOf(attacker));
	}

	@Test
	@DisplayName("T-271: the same outcome after the quiet window is told again")
	void sameOutcomeAfterQuietWindow_toldAgain() {
		Player ownerPlayer = member(1, overworld, 10, 10);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(16 * 60_000L);
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(List.of("Docks held against Rats", "Docks held against Rats"), linesOf(ownerPlayer));
	}

	@Test
	@DisplayName("T-271: a different attacking gang is told even within the quiet window")
	void differentAttacker_toldAgain() {
		Player ownerPlayer = member(1, overworld, 10, 10);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));
		clock.addAndGet(60_000L);
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 3));

		assertEquals(List.of("Docks held against Rats", "Docks held against Cobras"), linesOf(ownerPlayer));
	}

	@Test
	@DisplayName("cancelled is silent: an admin action tells nobody")
	void cancelled_isSilent() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		Player attacker = member(2, overworld, 80, 50);

		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.CANCELLED, 2));

		assertTrue(linesOf(ownerPlayer).isEmpty());
		assertTrue(linesOf(attacker).isEmpty());
	}

	@Test
	@DisplayName("captured tells the old owners they lost and the new owners they took it, and still broadcasts")
	void captured_tellsLoserAndWinner() {
		Player loser = member(1, overworld, 10, 10);
		Player winner = member(2, overworld, 80, 50);

		notifier.onCaptured(new TurfCapturedEvent(turf, owner, challenger));

		assertEquals(List.of("Docks lost to Rats"), linesOf(loser));
		assertEquals(List.of("You took Docks"), linesOf(winner));
		verify(legacy).broadcast(eq("TURF_CAPTURED_BROADCAST"),
		                         eq("turf"), eq("Docks"),
		                         eq("gang"), eq("Rats"),
		                         eq("old_gang"), eq("Vipers"));
	}

	@Test
	@DisplayName("a new start resets the half-guard and last-stand flags so a new contest can alert again")
	void perContestFlagsReset() {
		Player ownerPlayer = member(1, overworld, 10, 10);
		member(2, overworld, 80, 50);
		runtime.setChallengersInside(Map.of(2, 1));

		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		clock.addAndGet(20_000L);
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 3, 6));
		clock.addAndGet(20_000L);
		notifier.onCaptureProgress(progress(75.0));

		clock.addAndGet(20_000L);
		notifier.onCaptureFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		clock.addAndGet(20_000L);
		notifier.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		clock.addAndGet(20_000L);
		notifier.onDefenderDown(new TurfDefenderDownEvent(TURF_ID, 3, 6));
		clock.addAndGet(20_000L);
		notifier.onCaptureProgress(progress(75.0));

		List<String> lines = linesOf(ownerPlayer);
		assertEquals(2, lines.stream().filter(line -> line.equals("Half the guards at Docks are down")).count());
		assertEquals(2, lines.stream().filter(line -> line.startsWith("Last stand at Docks")).count());
	}

	// ---- helpers ----

	private static World world(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	private static Gang named(int id, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getDisplayNameString()).thenReturn(name);
		return gang;
	}

	/** An online player of gangId standing at (x, z) in world. */
	private Player member(int gangId, World world, int x, int z) {
		Player player = CaptureFixtures.player();
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		users.register(player, CaptureFixtures.userInGang(gangId));
		online.add(player);
		return player;
	}

	private TurfCaptureProgressEvent progress(double value) {
		return new TurfCaptureProgressEvent(turf, value, CapturePhase.CLAIM, value, 0.0);
	}

	/** Every line sent to the player, colour codes stripped. */
	private static List<String> linesOf(Player player) {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(player, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues().stream().map(ChatColor::stripColor).toList();
	}
}
