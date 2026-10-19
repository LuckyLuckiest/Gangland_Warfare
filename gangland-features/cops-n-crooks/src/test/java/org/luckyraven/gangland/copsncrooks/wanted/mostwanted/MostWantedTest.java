package org.luckyraven.gangland.copsncrooks.wanted.mostwanted;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.MostWantedSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-MW-1..6: the city's Most Wanted, held by earned stars only. */
@DisplayName("MostWanted")
class MostWantedTest {

	private static final long SEC = 1000L;
	private static final long T0  = 1_000_000L;

	@TempDir
	Path tempDir;

	private final UUID idA = new UUID(0, 1);
	private final UUID idB = new UUID(0, 2);
	private final UUID idC = new UUID(0, 3);

	private AtomicLong               clock;
	private ChaseArcs                arcs;
	private ChaseConfigLoader        config;
	private List<Player>             online;
	private List<String>             sent;
	private List<String>             radioCalls;
	private MostWanted               mw;
	private Player                  steve;
	private Player                  alex;
	private Player                  carl;
	private JavaPlugin               plugin;
	private BukkitStatics            bukkit;
	private MockedStatic<NpcSupport> npc;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install();
		npc    = Mockito.mockStatic(NpcSupport.class);
		npc.when(() -> NpcSupport.isNpc(any())).thenReturn(false);

		clock      = new AtomicLong(T0);
		arcs       = new ChaseArcs(clock::get);
		config     = mock(ChaseConfigLoader.class);
		when(config.getMostWanted()).thenReturn(MostWantedSettings.DEFAULT);
		online     = new ArrayList<>();
		sent       = new ArrayList<>();
		radioCalls = new ArrayList<>();
		plugin     = mock(JavaPlugin.class);

		steve = player(idA, "Steve");
		alex  = player(idB, "Alex");
		carl  = player(idC, "Carl");
		online.add(steve);
		online.add(alex);
		online.add(carl);

		mw = new MostWanted(plugin, arcs, config, HudFixtures.messages(tempDir), () -> online, sent::add,
		                    (p, stars) -> radioCalls.add(p.getName() + ":" + stars));
	}

	@AfterEach
	void tearDown() {
		npc.close();
		bukkit.close();
	}

	private Player player(UUID id, String name) {
		Player p = mock(Player.class);
		when(p.getUniqueId()).thenReturn(id);
		when(p.getName()).thenReturn(name);
		return p;
	}

	/** The clock moves to {@code ms} past T0 and one beat runs. */
	private void tickAt(long ms) {
		clock.set(T0 + ms);
		mw.tick(clock.get());
	}

	private String plain(String line) {
		return line.replaceAll("§.", "");
	}

	private void crimeStars(UUID id, int stars) {
		arcs.start(id, WantedCause.CRIME, stars);
	}

	@Test
	@DisplayName("four earned stars held ten seconds take the title: one broadcast naming him, one radio call")
	void fourEarnedStars_heldTenSeconds_takesTitle() {
		crimeStars(idA, 4);

		tickAt(0);
		tickAt(9 * SEC);
		assertNull(mw.holderName());
		assertTrue(sent.isEmpty());

		tickAt(10 * SEC);
		assertEquals("Steve", mw.holderName());
		assertEquals(4, mw.holderStars());
		assertEquals(1, sent.size());
		assertTrue(plain(sent.get(0)).contains("Steve"));
		assertTrue(plain(sent.get(0)).contains("Most Wanted"));
		assertTrue(plain(sent.get(0)).contains(plain(Wanted.buildStars(4, 5))));
		assertEquals(List.of("Steve:4"), radioCalls);
	}

	@Test
	@DisplayName("three earned stars never hold the title")
	void threeStars_neverHolds() {
		crimeStars(idA, 3);

		for (int s = 0; s <= 60; s += 5) {
			tickAt(s * SEC);
		}

		assertNull(mw.holderName());
		assertEquals(0, mw.holderStars());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("five stars bought at a sign earn nothing: no title")
	void signStars_cannotTakeTitle() {
		arcs.start(idA, WantedCause.SIGN, 5);

		tickAt(0);
		tickAt(30 * SEC);

		assertNull(mw.holderName());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("level 5 with one sign star is 4 earned: he holds, and holderStars reads 4")
	void mixedStars_countOnlyEarned() {
		crimeStars(idA, 4);
		arcs.earn(idA, 4, 5, WantedCause.SIGN);

		tickAt(0);
		tickAt(10 * SEC);

		assertEquals("Steve", mw.holderName());
		assertEquals(4, mw.holderStars());
	}

	@Test
	@DisplayName("an equal rival never unseats the holder; one more earned star does")
	void challengerNeedsMore() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);
		assertEquals("Steve", mw.holderName());

		clock.set(T0 + 11 * SEC);
		crimeStars(idB, 4);
		tickAt(11 * SEC);
		tickAt(41 * SEC);
		assertEquals("Steve", mw.holderName());

		arcs.earn(idB, 4, 5, WantedCause.CRIME);
		tickAt(42 * SEC);
		tickAt(52 * SEC);
		assertEquals("Alex", mw.holderName());
		assertEquals(5, mw.holderStars());
	}

	@Test
	@DisplayName("a later holder with more earned stars beats an earlier one: most earned first, earnedAt only breaks ties")
	void mostEarnedBeatsEarlierEarnedAt() {
		clock.set(T0);
		crimeStars(idA, 4);
		clock.set(T0 + 2 * SEC);
		crimeStars(idB, 5);

		tickAt(2 * SEC);
		tickAt(12 * SEC);

		assertEquals("Alex", mw.holderName());
		assertEquals(5, mw.holderStars());
		assertEquals(1, sent.size());
	}

	@Test
	@DisplayName("the production beat tick() reads the arcs clock")
	void tickUsesArcsClock() {
		crimeStars(idA, 4);

		mw.tick();
		assertNull(mw.holderName());

		clock.set(T0 + 10 * SEC);
		mw.tick();
		assertEquals("Steve", mw.holderName());
	}

	@Test
	@DisplayName("two equal candidates and no holder: the earlier earnedAt wins")
	void tieNoHolder_earliestEarnedAtWins() {
		clock.set(T0);
		crimeStars(idB, 4);
		clock.set(T0 + 2 * SEC);
		crimeStars(idA, 4);

		tickAt(2 * SEC);
		tickAt(12 * SEC);

		assertEquals("Alex", mw.holderName());
	}

	@Test
	@DisplayName("an exact tie on earnedAt falls to the lower UUID")
	void tieSameInstant_lowerUuidWins() {
		crimeStars(idB, 4);
		crimeStars(idA, 4);

		tickAt(0);
		tickAt(10 * SEC);

		assertEquals("Steve", mw.holderName());
	}

	@Test
	@DisplayName("the server hears a change of holder at most once per 120 s; the radio follows the broadcast")
	void broadcastCooldown120() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);
		assertEquals(1, sent.size());
		assertEquals(1, radioCalls.size());

		clock.set(T0 + 60 * SEC);
		crimeStars(idB, 5);
		tickAt(60 * SEC);
		tickAt(70 * SEC);
		assertEquals("Alex", mw.holderName());
		assertEquals(1, sent.size());
		assertEquals(1, radioCalls.size());

		clock.set(T0 + 130 * SEC);
		crimeStars(idC, 6);
		tickAt(130 * SEC);
		tickAt(140 * SEC);
		assertEquals("Carl", mw.holderName());
		assertEquals(2, sent.size());
		assertEquals("Carl:6", radioCalls.get(1));
	}

	@Test
	@DisplayName("a holder whose chase ends drops silently; the next qualified player takes it and is announced")
	void holderDrops_silent_passesToNext() {
		crimeStars(idA, 5);
		crimeStars(idB, 4);
		tickAt(0);
		tickAt(10 * SEC);
		assertEquals("Steve", mw.holderName());
		sent.clear();

		arcs.end(idA, true);
		tickAt(200 * SEC);

		assertEquals("Alex", mw.holderName());
		assertEquals(1, sent.size());

		sent.clear();
		arcs.end(idB, true);
		tickAt(210 * SEC);
		assertNull(mw.holderName());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("a holder who falls below the minimum loses the title silently")
	void holderBelowMin_losesTitle() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);
		sent.clear();

		arcs.earn(idA, 4, 3, WantedCause.UNKNOWN);
		tickAt(11 * SEC);

		assertNull(mw.holderName());
		assertEquals(0, mw.holderStars());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("quit(holder) clears the title without a word")
	void quit_dropsSilently() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);
		sent.clear();

		mw.quit(idA);

		assertNull(mw.holderName());
		assertEquals(0, mw.holderStars());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("quit(other) leaves the holder in place")
	void quit_otherPlayer_keepsHolder() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);

		mw.quit(idB);

		assertEquals("Steve", mw.holderName());
	}

	@Test
	@DisplayName("a player who is offline (arc kept) is not counted")
	void offlineArcIgnored() {
		crimeStars(idA, 4);
		arcs.quit(idA);
		online.remove(steve);

		tickAt(0);
		tickAt(30 * SEC);

		assertNull(mw.holderName());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("an arc with offlineAt set is ignored even if the player object is still listed")
	void offlineAtSet_ignoredWhileListed() {
		crimeStars(idA, 4);
		arcs.quit(idA);

		tickAt(0);
		tickAt(30 * SEC);

		assertNull(mw.holderName());
	}

	@Test
	@DisplayName("a dip below the minimum resets the qualifying clock: a fresh ten seconds are needed")
	void qualifyTimerResetsOnDip() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(8 * SEC);
		arcs.earn(idA, 4, 3, WantedCause.UNKNOWN);
		tickAt(9 * SEC);
		arcs.earn(idA, 3, 4, WantedCause.CRIME);
		tickAt(10 * SEC);
		tickAt(18 * SEC);
		assertNull(mw.holderName());

		tickAt(20 * SEC);
		assertEquals("Steve", mw.holderName());
	}

	@Test
	@DisplayName("holderStars follows the live count while he stays holder, with no new broadcast")
	void holderStarsFollowLiveCount() {
		crimeStars(idA, 5);
		tickAt(0);
		tickAt(10 * SEC);
		assertEquals(5, mw.holderStars());
		sent.clear();

		arcs.earn(idA, 5, 4, WantedCause.UNKNOWN);
		tickAt(11 * SEC);

		assertEquals("Steve", mw.holderName());
		assertEquals(4, mw.holderStars());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("Enable false clears the holder and stays silent")
	void disabled_clearsAndStaysSilent() {
		crimeStars(idA, 4);
		tickAt(0);
		tickAt(10 * SEC);
		assertEquals("Steve", mw.holderName());
		sent.clear();

		when(config.getMostWanted()).thenReturn(new MostWantedSettings(false, 4, 10, 120));
		tickAt(11 * SEC);

		assertNull(mw.holderName());
		assertEquals(0, mw.holderStars());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("a changed Min_Stars and Hold_Seconds are read live")
	void settingsReadLive() {
		when(config.getMostWanted()).thenReturn(new MostWantedSettings(true, 2, 0, 120));
		crimeStars(idA, 2);

		tickAt(0);

		assertEquals("Steve", mw.holderName());
	}

	@Test
	@DisplayName("a Citizens NPC with an arc never holds the title")
	void npcIgnored() {
		npc.when(() -> NpcSupport.isNpc(steve)).thenReturn(true);
		crimeStars(idA, 5);

		tickAt(0);
		tickAt(30 * SEC);

		assertNull(mw.holderName());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("a player with no arc is skipped without error")
	void noArc_skipped() {
		tickAt(0);
		tickAt(30 * SEC);

		assertNull(mw.holderName());
	}

	@Test
	@DisplayName("onInitialize schedules one synchronous 20/20 timer; a second call is a no-op; onClear cancels it")
	void timerSyncOnStart() {
		BukkitTask task = mock(BukkitTask.class);
		when(bukkit.scheduler().runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L))).thenReturn(task);

		mw.onInitialize(true);
		mw.onInitialize(false);
		verify(bukkit.scheduler(), times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));

		mw.onClear();
		verify(task).cancel();

		mw.onInitialize(false);
		verify(bukkit.scheduler(), times(2)).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));
	}
}
