package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the heat ledger: weighted crimes add heat, streak/cop-sight/turf-war multipliers apply, and crossing star
 * thresholds replays the star trigger until the wanted level matches.
 */
@DisplayName("HeatLedger")
class HeatLedgerTest {

	private final UUID playerId = UUID.randomUUID();

	private Player            player;
	private Location          location;
	private Wanted            wanted;
	private CrimeService      crimes;
	private ChaseConfigLoader config;
	private AtomicLong        clock;
	private AtomicInteger     window;
	private AtomicBoolean     copSight;
	private AtomicBoolean     contested;
	private AtomicInteger     triggers;
	private HeatLedger        ledger;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(location);
		when(location.clone()).thenReturn(location);

		wanted = new Wanted(null, 1, 5);
		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		config = mock(ChaseConfigLoader.class);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);

		crimes    = mock(CrimeService.class);
		clock     = new AtomicLong(1_000_000L);
		window    = new AtomicInteger(10);
		copSight  = new AtomicBoolean();
		contested = new AtomicBoolean();
		triggers  = new AtomicInteger();

		ledger = new HeatLedger(config, users, crimes, p -> copSight.get(), l -> contested.get(), window::get,
		                        clock::get);
		ledger.setStarTrigger(p -> {
			triggers.incrementAndGet();
			wanted.setLevel(wanted.getLevel() + 1);
		});
	}

	private CrimeCommittedEvent crime(String id) {
		return new CrimeCommittedEvent(player, id, location, false, 0);
	}

	private void heatOff() {
		HeatSettings d = HeatSettings.DEFAULT;
		HeatSettings off = new HeatSettings(false, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                    d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights());
		when(config.get()).thenReturn(new ChaseConfig(off, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
	}

	@Test
	@DisplayName("one Assault_Cop gives the first star and is the last crime")
	void assaultCop_once_givesTheFirstStar_andIsTheLastCrime() {
		assertEquals(100, ledger.record(crime(Crimes.ASSAULT_COP)));

		assertEquals(1, wanted.getLevel());
		assertEquals(1, triggers.get());
		assertEquals(Crimes.ASSAULT_COP, ledger.lastCrime(playerId).crimeId());
	}

	@Test
	@DisplayName("two Kill_Cop inside the streak window reach two stars (150 + 225)")
	void killCopTwiceInTheStreakWindow_reachesTwoStars() {
		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));
		clock.addAndGet(5_000);
		assertEquals(225, ledger.record(crime(Crimes.KILL_COP)));

		assertEquals(375, ledger.heatOf(playerId));
		assertEquals(2, wanted.getLevel());
	}

	@Test
	@DisplayName("a crime after the streak window gets no streak bonus")
	void crimeAfterTheWindow_hasNoStreakBonus() {
		ledger.record(crime(Crimes.KILL_COP));
		clock.addAndGet(11_000);

		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));
	}

	@Test
	@DisplayName("a crime the publisher saw a cop witness is multiplied by 1.5")
	void seenByCop_multipliesByOneAndAHalf() {
		CrimeCommittedEvent seen = new CrimeCommittedEvent(player, Crimes.KILL_COP, location, true, 1);

		assertEquals(225, ledger.record(seen));
	}

	@Test
	@DisplayName("the squad's own sight alone also multiplies by 1.5")
	void copSight_aloneAlsoMultiplies() {
		copSight.set(true);

		assertEquals(225, ledger.record(crime(Crimes.KILL_COP)));
	}

	@Test
	@DisplayName("a contested turf halves Kill_Player only")
	void turfWar_halvesKillPlayerOnlyInsideAContestedTurf() {
		contested.set(true);
		assertEquals(40, ledger.record(crime(Crimes.KILL_PLAYER)));

		ledger.clear(playerId);
		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));

		ledger.clear(playerId);
		contested.set(false);
		assertEquals(80, ledger.record(crime(Crimes.KILL_PLAYER)));
	}

	@Test
	@DisplayName("a Jailbreak crosses three thresholds and triggers three times")
	void jailbreak_crossesThreeThresholds_triggersThreeTimes() {
		assertEquals(450, ledger.record(crime("Jailbreak")));

		assertEquals(3, triggers.get());
		assertEquals(3, wanted.getLevel());
	}

	@Test
	@DisplayName("an unknown crime and a disabled heat add nothing")
	void unknownCrime_andHeatDisabled_addNothing() {
		assertEquals(0, ledger.record(crime("Not_A_Crime")));
		assertNull(ledger.lastCrime(playerId));

		heatOff();
		assertEquals(0, ledger.record(crime(Crimes.KILL_COP)));
		assertEquals(0, triggers.get());
		assertTrue(ledger.chaseCrimes(playerId).isEmpty());
	}

	@Test
	@DisplayName("a level drop floors the heat at the new level's threshold")
	void levelDrop_floorsTheHeat() {
		ledger.record(crime("Jailbreak"));
		ledger.record(crime("Jailbreak"));

		ledger.onLevelChanged(playerId, 5, 2, 5, WantedCause.EVASION);

		assertEquals(250, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("an admin raise lifts the heat to the new level's floor")
	void adminRaise_liftsHeatToTheFloor() {
		ledger.record(crime(Crimes.ASSAULT_COP));

		ledger.onLevelChanged(playerId, 1, 4, 5, WantedCause.ADMIN);

		assertEquals(700, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("a crime-caused raise leaves the heat alone")
	void crimeRaise_leavesTheHeat() {
		ledger.record(crime(Crimes.ASSAULT_COP));

		ledger.onLevelChanged(playerId, 0, 4, 5, WantedCause.CRIME);

		assertEquals(100, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("a restored chase starts at the floor of its level")
	void restoredChase_startsAtTheFloorOfItsLevel() {
		ledger.onLevelChanged(playerId, 0, 3, 5, WantedCause.RESTORE);

		assertEquals(450, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("chase crimes keep their order and clear empties them")
	void chaseCrimes_keepOrder_andClearEmptiesThem() {
		ledger.record(crime(Crimes.ASSAULT_COP));
		ledger.record(crime(Crimes.KILL_COP));

		List<String> ids = ledger.chaseCrimes(playerId).stream().map(CrimeRecord::crimeId).toList();
		assertEquals(List.of(Crimes.ASSAULT_COP, Crimes.KILL_COP), ids);

		ledger.clear(playerId);

		assertTrue(ledger.chaseCrimes(playerId).isEmpty());
		assertEquals(0, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("the same victim inside the window commits once, then again after it")
	void reportAssault_sameVictimInsideTheWindow_commitsOnce_thenAgainAfterIt() {
		UUID victim = UUID.randomUUID();

		ledger.reportAssault(player, victim, location);
		clock.addAndGet(5_000);
		ledger.reportAssault(player, victim, location);
		verify(crimes, times(1)).commit(player, Crimes.ASSAULT_COP, location);

		ledger.reportAssault(player, UUID.randomUUID(), location);
		verify(crimes, times(2)).commit(player, Crimes.ASSAULT_COP, location);

		clock.addAndGet(6_000);
		ledger.reportAssault(player, victim, location);
		verify(crimes, times(3)).commit(player, Crimes.ASSAULT_COP, location);
	}
}
