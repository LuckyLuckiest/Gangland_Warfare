package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.events.user.UserBountyEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedKillTracker;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The kill path hands every star change to {@link WantedStars} with its cause (CONTRACTS C18) and sends every counted
 * kill to an installed tracker whatever {@code Wanted.Kill_Combo.Enable} says (the tracker applies the switch itself,
 * C12). Users are real, so the assertions read the actual level, the events and the scheduled clock.
 */
@DisplayName("EntityDamageListener - kills, star routing and the death reset")
class EntityDamageListenerTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private WantedStars         stars;
	private UserManager<Player> userManager;
	private Player              alice, bob;
	private User<Player>        aliceUser, bobUser;
	private List<Event>         events;
	private GangMembership      gangs;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install();
		gangs  = new GangMembership();
		SettingsFixture.write(dir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				Bounty:
				  Repeating_Timer:
				    Enable: false
				Wanted:
				  Kill_Combo:
				    Enable: false
				""");
		SettingsFixture.initialize(dir);
		Messages.init(new FakeMessageProvider());
		EconomyHandler.setVaultEconomy(null);
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);

		events = new ArrayList<>();
		doAnswer(invocation -> {
			events.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		when(identity.getWantedLevelIncrement()).thenReturn(1);
		when(identity.getWantedMaximumLevel()).thenReturn(5);
		IdentitySettings.bind(identity);

		plugin = mock(JavaPlugin.class);
		WantedSettings settings = mock(WantedSettings.class);
		when(settings.isTimerEnabled()).thenReturn(true);
		when(settings.getTimerTime()).thenReturn(120);
		stars = new WantedStars(plugin, settings);

		userManager = mock(UserManager.class);
		alice       = player("Alice");
		bob         = player("Bob");
		aliceUser   = new User<>(plugin, alice, (p, raw) -> raw);
		bobUser     = new User<>(plugin, bob, (p, raw) -> raw);
		when(userManager.getUser(alice)).thenReturn(aliceUser);
		when(userManager.getUser(bob)).thenReturn(bobUser);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a player kill raises with the CRIME cause and starts a SYNC decay clock")
	void kill_raisesWithCrimeCause_andStartsASyncClock() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
		assertEquals(WantedCause.CRIME, lastChange().getCause());
		assertNotNull(aliceUser.getWanted().getRepeatingTimer());
		verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	@Test
	@DisplayName("with the star chat line suppressed (the HUD star card) the killer gets no chat line")
	void suppressedStarChat_sendsNoChatLine() {
		stars.suppressStarChat(() -> true);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel(), "the star still lands");
		verify(alice, never()).sendMessage(contains("WANTED LEVEL"));
	}

	@Test
	@DisplayName("without a suppressor the chat line is sent as before")
	void starChat_isSentWhenNotSuppressed() {
		kill(listener(new WantedKillTrackers()), alice, bob);

		verify(alice).sendMessage(contains("WANTED LEVEL"));
	}

	@Test
	@DisplayName("Kill_Combo.Enable false does not bypass a tracker that applies the switch itself: it gets the kill")
	void comboDisabled_activeTracker_stillGetsTheKill() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  tracker  = mock(WantedKillTracker.class);
		when(tracker.appliesComboSwitch()).thenReturn(true);
		trackers.install(tracker);

		kill(listener(trackers), alice, bob);

		verify(tracker).recordKill(eq(alice), eq(aliceUser.getWanted()), eq(bob), anyInt());
		assertEquals(0, aliceUser.getWanted().getLevel(), "the tracker decides the stars, not the listener");
	}

	@Test
	@DisplayName("a pre-0.15 tracker (Host_Api 2.0 jar) keeps the core's Kill_Combo.Enable gate: combo off = one star")
	void comboDisabled_trackerWithoutTheSwitch_keepsTheCoreGate() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  tracker  = mock(WantedKillTracker.class);   // appliesComboSwitch() answers false
		trackers.install(tracker);

		kill(listener(trackers), alice, bob);

		verify(tracker, never()).recordKill(any(), any(), any(), anyInt());
		assertEquals(1, aliceUser.getWanted().getLevel(), "the legacy one-star-per-kill path");
	}

	@Test
	@DisplayName("TF-49: a kill the tracker exempts (defending your own turf) adds no notoriety and starts no timer")
	void turfDefenderKill_addsNoNotoriety_andStartsNoBountyTimer() throws IOException {
		bountySettings("""
				  Repeating_Timer:
				    Enable: true
				""");
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  tracker  = mock(WantedKillTracker.class);
		when(tracker.appliesComboSwitch()).thenReturn(true);
		when(tracker.exemptsKill(alice, bob)).thenReturn(true);
		trackers.install(tracker);

		kill(listener(trackers), alice, bob);

		assertEquals(0, aliceUser.getBounty().getNotoriety().signum(), "no Kill.Each notoriety");
		assertNull(aliceUser.getBounty().getRepeatingTimer(), "no bounty timer");
		verify(tracker, never()).recordKill(any(), any(), any(), anyInt());
		assertEquals(1, aliceUser.getKills(), "still a kill on the scoreboard");
	}

	@Test
	@DisplayName("a death reset for a player who already left does not throw")
	void deathResetForAnOfflinePlayer_doesNotThrow() {
		Consumer<UUID> reset = deathReset();

		assertDoesNotThrow(() -> reset.accept(UUID.randomUUID()));
	}

	@Test
	@DisplayName("a death reset clears the stars with the DEATH cause")
	void deathReset_resetsWithDeathCause() {
		Consumer<UUID> reset = deathReset();
		aliceUser.getWanted().setLevel(3);
		events.clear();
		bukkit.statics().when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);

		reset.accept(alice.getUniqueId());

		assertEquals(0, aliceUser.getWanted().getLevel());
		assertEquals(WantedCause.DEATH, lastChange().getCause());
	}

	@Test
	@DisplayName("a claim pays only what players posted; the server-made notoriety stays on the victim")
	void claim_paysPostedOnly_notorietyStays() {
		post(bobUser, "poster", 1000);
		bobUser.getBounty().addNotoriety(BigDecimal.valueOf(500));

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, BigDecimal.valueOf(1000).compareTo(aliceUser.getEconomy().getAmount()));
		assertEquals(0, BigDecimal.valueOf(500).compareTo(bobUser.getBounty().getAmount()));
		assertEquals(0, bobUser.getBounty().getPostedAmount().signum());
	}

	@Test
	@DisplayName("a takedown of a posted bounty is not a crime: no star, no notoriety")
	void postedTakedown_isNotACrime() {
		post(bobUser, "poster", 1000);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, aliceUser.getWanted().getLevel());
		assertEquals(0, aliceUser.getBounty().getAmount().signum());
	}

	@Test
	@DisplayName("a target with notoriety only pays nothing, and the kill is a crime")
	void notorietyOnlyTarget_paysNothing_andTheKillIsACrime() {
		bobUser.getBounty().addNotoriety(BigDecimal.valueOf(500));

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, aliceUser.getEconomy().getAmount().signum());
		assertEquals(0, BigDecimal.valueOf(500).compareTo(bobUser.getBounty().getAmount()));
		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("Pay_Notoriety true pays everything once and resets the bounty")
	void payNotorietyTrue_paysEverythingOnce_andResets() throws IOException {
		SettingsFixture.write(dir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				Bounty:
				  Pay_Notoriety: true
				  Repeating_Timer:
				    Enable: false
				""");
		SettingsFixture.initialize(dir);
		post(bobUser, "poster", 1000);
		bobUser.getBounty().addNotoriety(BigDecimal.valueOf(500));
		EntityDamageListener listener = listener(new WantedKillTrackers());

		kill(listener, alice, bob);

		assertEquals(0, BigDecimal.valueOf(1500).compareTo(aliceUser.getEconomy().getAmount()));
		assertFalse(bobUser.getBounty().hasBounty());
		assertEquals(0, aliceUser.getWanted().getLevel(), "the first kill was a takedown");

		kill(listener, alice, bob);

		assertEquals(0, BigDecimal.valueOf(1500).compareTo(aliceUser.getEconomy().getAmount()), "paid once");
	}

	@Test
	@DisplayName("WB-43: a gangmate or ally collects nothing and the bounty stays")
	void gangmatesOrAllies_collectNothing() {
		GangMembershipView view = mock(GangMembershipView.class);
		when(view.gangIdOf(any())).thenReturn(7);
		gangs.install(view);
		post(bobUser, "poster", 1000);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, aliceUser.getEconomy().getAmount().signum());
		assertEquals(0, BigDecimal.valueOf(1000).compareTo(bobUser.getBounty().getAmount()));
	}

	@Test
	@DisplayName("a bounty saved before the upgrade (no ledger) is still paid in full, once")
	void legacyBounty_paysInFullOnce() {
		bobUser.getBounty().setAmount(BigDecimal.valueOf(300));
		bobUser.getBounty().restoreLedger(null);
		EntityDamageListener listener = listener(new WantedKillTrackers());

		kill(listener, alice, bob);
		kill(listener, alice, bob);

		assertEquals(0, BigDecimal.valueOf(300).compareTo(aliceUser.getEconomy().getAmount()));
		assertFalse(bobUser.getBounty().hasBounty());
	}

	@Test
	@DisplayName("the victim struck first: the kill is self-defence, so no star and no notoriety")
	void victimStruckFirst_killIsSelfDefence_noStarNoNotoriety() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice);
		kill(listener, alice, bob);

		assertEquals(0, aliceUser.getWanted().getLevel());
		assertEquals(0, aliceUser.getBounty().getAmount().signum());
	}

	@Test
	@DisplayName("the killer struck first: the kill is a crime even if the victim hit back")
	void killerStruckFirst_killIsACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, alice, bob);
		hit(listener, bob, alice);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("a fight with no hit for longer than Self_Defence.Window_Seconds (8) is forgotten, so a later kill is a crime")
	void fightOlderThanTheWindow_isForgotten_soTheKillIsACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];

		hit(listener, bob, alice);
		now[0] = 9_000L;
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: the victim's damage to the killer must reach Self_Defence.Min_Damage (2.0)")
	void victimBelowMinDamage_killIsACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice, 1.0);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36 (characterization pin): two light hits adding up to the minimum, struck first, are self-defence")
	void victimHitFirstWithEnoughDamage_isSelfDefence() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice, 1.0);
		hit(listener, bob, alice, 1.0);
		kill(listener, alice, bob);

		assertEquals(0, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: a gangmate's first strike never counts as self-defence")
	void gangmateFirstStrike_neverCounts() {
		GangMembershipView view = mock(GangMembershipView.class);
		when(view.gangIdOf(any())).thenReturn(7);
		gangs.install(view);
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: a second exemption for the same pair inside Pair_Cooldown_Seconds is a crime")
	void secondExemptionInsideThePairCooldown_isACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice);
		kill(listener, alice, bob);
		assertEquals(0, aliceUser.getWanted().getLevel(), "the first one is self-defence");

		hit(listener, bob, alice);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: Self_Defence.Enable false means no self-defence exemption at all")
	void selfDefenceDisabled_killIsACrime() throws IOException {
		SettingsFixture.write(dir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				Bounty:
				  Repeating_Timer:
				    Enable: false
				Wanted:
				  Self_Defence:
				    Enable: false
				  Kill_Combo:
				    Enable: false
				""");
		SettingsFixture.initialize(dir);
		EntityDamageListener listener = listener(new WantedKillTrackers());

		hit(listener, bob, alice);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: provoke, wait out the window, let the victim retaliate: the kill is a crime")
	void provokeWaitRetaliate_isACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];

		hit(listener, alice, bob);
		now[0] = 9_000L;
		hit(listener, bob, alice, 4.0);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: the same retaliation after the 60 s provocation memory is self-defence again")
	void retaliationAfterTheProvocationMemory_isSelfDefence() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];

		hit(listener, alice, bob);
		now[0] = 61_000L;
		hit(listener, bob, alice, 4.0);
		kill(listener, alice, bob);

		assertEquals(0, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R37, WB-48: a takedown of a bounty below Takedown_Minimum pays but is a crime")
	void takedownBelowTakedownMinimum_paysButIsACrime() {
		bobUser.getBounty().addBounty(poster("poster"), new BigDecimal("0.01"), 0);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, new BigDecimal("0.01").compareTo(aliceUser.getEconomy().getAmount()), "still paid");
		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R37 (characterization pin): a takedown at exactly Takedown_Minimum (100) is not a crime")
	void takedownAtTheMinimum_isNotACrime() {
		post(bobUser, "poster", 100);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, BigDecimal.valueOf(100).compareTo(aliceUser.getEconomy().getAmount()));
		assertEquals(0, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R37: a second takedown of the same pair inside the pair cooldown is a crime")
	void takedownInsideThePairCooldown_isACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		post(bobUser, "poster", 1000);
		kill(listener, alice, bob);
		assertEquals(0, aliceUser.getWanted().getLevel());

		post(bobUser, "poster", 1000);
		kill(listener, alice, bob);

		assertEquals(0, BigDecimal.valueOf(2000).compareTo(aliceUser.getEconomy().getAmount()), "still paid");
		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R37, D20: the fourth takedown by one killer inside the hour pays but is a crime")
	void fourthTakedownWithinTheHour_paysButIsACrime() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		List<User<Player>>   victims  = new ArrayList<>();

		for (int i = 0; i < 4; i++) {
			Player           extra = player("Victim" + i);
			User<Player>     user  = new User<>(plugin, extra, (p, raw) -> raw);
			when(userManager.getUser(extra)).thenReturn(user);
			post(user, "poster", 1000);
			victims.add(user);
		}

		for (int i = 0; i < 3; i++) {
			kill(listener, alice, victims.get(i).getUser());
			assertEquals(0, aliceUser.getWanted().getLevel(), "takedown " + (i + 1) + " is crime-free");
		}

		kill(listener, alice, victims.get(3).getUser());

		assertEquals(0, BigDecimal.valueOf(4000).compareTo(aliceUser.getEconomy().getAmount()), "all four paid");
		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: pruning drops the anti-abuse memories by time; a quit drops only the fight state")
	void pruneAndQuit_dropTheNewMemories() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];
		Player carol = player("Carol");
		Player dave  = player("Dave");
		when(userManager.getUser(carol)).thenReturn(aliceUser);
		when(userManager.getUser(dave)).thenReturn(bobUser);

		hit(listener, bob, alice);
		kill(listener, alice, bob);
		post(bobUser, "poster", 1000);
		kill(listener, alice, bob);
		assertTrue(listener.trackedMemories() > 0);

		now[0] = 3_700_000L;
		hit(listener, carol, dave);
		assertEquals(3, listener.trackedMemories(), "only carol>dave: its damage, last hit and provocation flag remain");

		listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(carol, "bye"));
		assertEquals(1, listener.trackedMemories(), "the carol>dave last-hit stamp waits for its 60 s prune");
	}

	@Test
	@DisplayName("R37, D20: relogging does not reset the killer's crime-free takedown tally")
	void relog_keepsTheTakedownTally() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		List<User<Player>>   victims  = new ArrayList<>();

		for (int i = 0; i < 4; i++) {
			Player       extra = player("Victim" + i);
			User<Player> user  = new User<>(plugin, extra, (p, raw) -> raw);
			when(userManager.getUser(extra)).thenReturn(user);
			post(user, "poster", 1000);
			victims.add(user);
		}

		for (int i = 0; i < 3; i++) kill(listener, alice, victims.get(i).getUser());
		listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(alice, "relog"));
		kill(listener, alice, victims.get(3).getUser());

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R37: the victim relogging does not reset the pair cooldown")
	void relog_keepsThePairCooldown() {
		EntityDamageListener listener = listener(new WantedKillTrackers());

		post(bobUser, "poster", 1000);
		kill(listener, alice, bob);
		listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(bob, "relog"));
		post(bobUser, "poster", 1000);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("R36: provoke, relog, let the victim retaliate: the kill is still a crime")
	void relog_keepsTheProvocation() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];

		hit(listener, alice, bob);
		listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(alice, "relog"));
		now[0] = 9_000L;
		hit(listener, bob, alice, 4.0);
		kill(listener, alice, bob);

		assertEquals(1, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("hits from an NPC attacker are not recorded in the fight map")
	void npcAttackerHits_areNotRecorded() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		Player               npc      = player("Cop");

		hit(listener, npc, alice);

		assertEquals(0, listener.trackedFights());
	}

	@Test
	@DisplayName("fights older than the 30 s window are pruned by the next recorded hit, and a quit forgets the player")
	void staleFights_arePruned_andQuitForgets() {
		EntityDamageListener listener = listener(new WantedKillTrackers());
		long[] now = {0L};
		listener.clock = () -> now[0];
		Player carol = player("Carol");
		Player dave  = player("Dave");
		when(userManager.getUser(carol)).thenReturn(aliceUser);
		when(userManager.getUser(dave)).thenReturn(bobUser);

		hit(listener, alice, bob);
		assertEquals(1, listener.trackedFights());

		now[0] = 31_000L;
		hit(listener, carol, dave);
		assertEquals(1, listener.trackedFights(), "the alice/bob fight is pruned, only carol/dave remains");

		listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(carol, "bye"));
		assertEquals(0, listener.trackedFights());
	}

	@Test
	@DisplayName("self-defence holds with an installed tracker too: the tracker is never told")
	void selfDefence_alsoHoldsWithoutTheTracker() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  tracker  = mock(WantedKillTracker.class);
		trackers.install(tracker);
		EntityDamageListener active = listener(trackers);

		hit(active, bob, alice);
		kill(active, alice, bob);
		verify(tracker, never()).recordKill(any(), any(), any(), anyInt());

		// and with no tracker installed the legacy handleWanted path stays silent
		EntityDamageListener inactive = listener(new WantedKillTrackers());
		hit(inactive, bob, alice);
		kill(inactive, alice, bob);
		assertEquals(0, aliceUser.getWanted().getLevel());
	}

	@Test
	@DisplayName("a player killing himself (his own arrow) claims nothing, keeps the ledger and commits no crime")
	void selfKill_claimsNothing() {
		post(bobUser, "poster", 1000);

		kill(listener(new WantedKillTrackers()), bob, bob);

		assertEquals(0, bobUser.getEconomy().getAmount().signum(), "no payout to himself");
		assertEquals(0, BigDecimal.valueOf(1000).compareTo(bobUser.getBounty().getPostedAmount()), "ledger kept");
		assertEquals(0, bobUser.getWanted().getLevel());
		assertEquals(0, bobUser.getKills());
	}

	@Test
	@DisplayName("a bounty the killer posted himself pays his escrow back, but the kill is still a crime")
	void selfPostedBounty_killIsStillACrime() {
		bobUser.getBounty().addBounty(alice, BigDecimal.valueOf(100));

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, BigDecimal.valueOf(100).compareTo(aliceUser.getEconomy().getAmount()), "own money back");
		assertEquals(1, aliceUser.getWanted().getLevel(), "only other players' escrow makes a takedown");
	}

	@Test
	@DisplayName("a kill at the maximum stars adds no auto bounty: no star landed")
	void killAtMaxWanted_addsNoAutoBounty() {
		aliceUser.getWanted().setLevel(5);
		aliceUser.getBounty().setBaseAmount(BigDecimal.TEN);

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(5, aliceUser.getWanted().getLevel());
		assertEquals(0, killEach(aliceUser).compareTo(aliceUser.getBounty().getNotoriety()),
		             "only the Kill.Each part, no auto bounty for a star that never landed");
	}

	@Test
	@DisplayName("WB-14: with the repeating timer on, a crime kill adds the Kill.Each notoriety and starts a SYNC timer")
	void killWithTimerOn_addsKillEachNotoriety_andStartsTheTimer() throws IOException {
		bountySettings("""
				  Repeating_Timer:
				    Enable: true
				""");

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(1, killEach(aliceUser).signum(), "fixture sanity: Kill.Each is positive");
		assertEquals(0, killEach(aliceUser).compareTo(aliceUser.getBounty().getNotoriety()));
		assertNotNull(aliceUser.getBounty().getRepeatingTimer(), "the notoriety timer runs");
		verify(bukkit.scheduler(), never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
		                                                               anyLong(), anyLong());
	}

	@Test
	@DisplayName("WB-14: a Kill.Each that would pass Kill.Maximum is skipped")
	void killEach_overKillMaximum_isSkipped() throws IOException {
		bountySettings("""
				  Kill:
				    Each: 5
				    Maximum: 3
				  Repeating_Timer:
				    Enable: false
				""");

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, aliceUser.getBounty().getNotoriety().signum());
		assertTrue(events.stream().noneMatch(UserBountyEvent.class::isInstance), "no bounty event either");
	}

	@Test
	@DisplayName("WB-14: a cancelled bounty event adds no Kill.Each notoriety")
	void cancelledBountyEvent_addsNothing() {
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			if (event instanceof UserBountyEvent bounty) bounty.setCancelled(true);
			events.add(event);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());

		kill(listener(new WantedKillTrackers()), alice, bob);

		assertEquals(0, aliceUser.getBounty().getNotoriety().signum());
	}

	private static BigDecimal killEach(User<Player> killer) {
		return killer.getBounty().calculateLevelScaledBounty(Settings.getBountyEachKillValue(),
		                                                     killer.getLevel().getLevelValue());
	}

	/** Re-reads settings.yml with {@code bountyBlock} (2-space indented keys) under {@code Bounty:}. */
	private void bountySettings(String bountyBlock) throws IOException {
		SettingsFixture.write(dir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				Bounty:
				""" + bountyBlock);
		SettingsFixture.initialize(dir);
	}

	private void post(User<Player> target, String poster, int amount) {
		CommandSender sender = mock(CommandSender.class);
		when(sender.getName()).thenReturn(poster);
		target.getBounty().addBounty(sender, BigDecimal.valueOf(amount), 0);
	}

	private static CommandSender poster(String name) {
		CommandSender sender = mock(CommandSender.class);
		when(sender.getName()).thenReturn(name);
		return sender;
	}

	/** A hit of 4.0, above Self_Defence.Min_Damage (2.0): the helper is fixed, never the rule. */
	private void hit(EntityDamageListener listener, Player attacker, Player victim) {
		hit(listener, attacker, victim, 4.0);
	}

	private void hit(EntityDamageListener listener, Player attacker, Player victim, double damage) {
		when(victim.getHealth()).thenReturn(20.0);
		when(victim.getLocation()).thenReturn(new Location(null, 0, 0, 0));

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(attacker);
		when(event.getEntity()).thenReturn(victim);
		when(event.getFinalDamage()).thenReturn(damage);

		listener.onPlayerEntityDeath(event);
	}

	@SuppressWarnings("unchecked")
	private Consumer<UUID> deathReset() {
		WantedKillTrackers trackers = new WantedKillTrackers();
		WantedKillTracker  tracker  = mock(WantedKillTracker.class);
		trackers.install(tracker);
		listener(trackers);

		ArgumentCaptor<Consumer<UUID>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(tracker).onVictimDeath(captor.capture());
		return captor.getValue();
	}

	/** The only place the listener is constructed, so a constructor change touches one line. */
	private EntityDamageListener listener(WantedKillTrackers trackers) {
		return new EntityDamageListener(mock(Gangland.class), userManager, trackers, mock(BountySettings.class),
		                                mock(WantedSettings.class), stars, gangs);
	}

	private void kill(EntityDamageListener listener, Player killer, Player victim) {
		when(victim.getHealth()).thenReturn(1.0);
		when(victim.getLocation()).thenReturn(new Location(null, 0, 0, 0));

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(killer);
		when(event.getEntity()).thenReturn(victim);
		when(event.getFinalDamage()).thenReturn(20.0);

		listener.onPlayerEntityDeath(event);
	}

	private WantedLevelChangeEvent lastChange() {
		return events.stream()
		             .filter(WantedLevelChangeEvent.class::isInstance)
		             .map(WantedLevelChangeEvent.class::cast)
		             .reduce((first, second) -> second)
		             .orElseThrow(() -> new AssertionError("no WantedLevelChangeEvent fired"));
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getPlayer()).thenReturn(player);
		when(player.isOnline()).thenReturn(true);
		return player;
	}

}
