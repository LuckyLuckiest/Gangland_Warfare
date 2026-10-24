package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.CrewHeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrewHeatListener} (0.21.0, P-CREW-1..6): a crime is recorded straight into every online gang mate's or
 * ally's own heat ledger within the radius, never re-shared, never to outsiders, restrained, downed, NPC or
 * defending mates.
 */
@DisplayName("CrewHeatListener - crimes are shared with the crew")
class CrewHeatListenerTest {

	private static final int OWN_GANG   = 1;
	private static final int ALLY_GANG  = 2;
	private static final int OTHER_GANG = 3;

	private final Map<UUID, Integer> gangOf  = new HashMap<>();
	private final List<Player>       inWorld = new ArrayList<>();
	private final List<UUID>         downed  = new ArrayList<>();
	private final Map<Player, Wanted> wanteds = new HashMap<>();

	private HeatLedger        ledger;
	private TurfManager       turfs;
	private DetainmentService detainment;
	private ChaseConfigLoader config;
	private WantedMessages    messages;
	private GangMembership    gangs;
	private World             world;
	private Location          crimeLoc;
	private Player            actor;
	private CrewHeatListener  listener;

	private MockedStatic<NpcSupport> npcSupport;
	private MockedStatic<Bukkit>     bukkit;
	private PluginManager            pluginManager;

	@BeforeEach
	void setUp() {
		ledger     = mock(HeatLedger.class);
		turfs      = mock(TurfManager.class);
		detainment = mock(DetainmentService.class);
		config     = mock(ChaseConfigLoader.class);
		messages   = mock(WantedMessages.class);
		world      = mock(World.class);
		crimeLoc   = mock(Location.class);

		when(crimeLoc.clone()).thenReturn(crimeLoc);
		when(crimeLoc.getWorld()).thenReturn(world);
		when(world.getPlayers()).thenReturn(inWorld);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);
		when(config.getCrewHeat()).thenReturn(CrewHeatSettings.DEFAULT);
		when(ledger.record(any())).thenReturn(150.0);
		when(messages.format(eq(WantedMessages.Key.CREW_HEAT_SHARED), anyMap())).thenReturn("shared-line");
		when(messages.crimeName(any())).thenReturn("Kill Civilian");

		gangs = new GangMembership();
		gangs.install(new GangMembershipView() {
			@Override
			public int gangIdOf(UUID uuid) {
				return gangOf.getOrDefault(uuid, -1);
			}

			@Override
			public boolean gangsAllied(int a, int b) {
				return (a == OWN_GANG && b == ALLY_GANG) || (a == ALLY_GANG && b == OWN_GANG);
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				return Optional.empty();
			}
		});

		actor    = player("Steve", OWN_GANG, 0);
		listener = new CrewHeatListener(ledger, gangs, turfs, detainment, config, messages);

		npcSupport = Mockito.mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);

		pluginManager = mock(PluginManager.class);
		bukkit        = Mockito.mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
		bukkit.close();
		downed.forEach(DownedPlayerRegistry::remove);
	}

	/** An online player of {@code gang} (-1 = none) standing {@code distSq} (squared blocks) from the crime spot. */
	private Player player(String name, int gang, double distSq) {
		Player   p  = mock(Player.class);
		UUID     id = UUID.randomUUID();
		Location at = mock(Location.class);
		when(p.getUniqueId()).thenReturn(id);
		when(p.getName()).thenReturn(name);
		when(p.getLocation()).thenReturn(at);
		when(at.distanceSquared(crimeLoc)).thenReturn(distSq);
		gangOf.put(id, gang);
		inWorld.add(p);
		return p;
	}

	private CrimeCommittedEvent crime(String id) {
		return new CrimeCommittedEvent(actor, id, crimeLoc, true, 3);
	}

	private void share(String id) {
		listener.onCrime(crime(id));
	}

	private static boolean recordsFor(CrimeCommittedEvent e, Player p, String id) {
		return e.getPlayer() == p && e.getCrimeId().equals(id);
	}

	@Test
	@DisplayName("a gang mate within the radius gets the same crime id and spot in his own ledger")
	void gangMateWithinRadius_getsSameCrime() {
		Player mate = player("Alex", OWN_GANG, 25);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(argThat(e -> recordsFor(e, mate, Crimes.KILL_CIVILIAN)
		                                             && e.getLocation() == crimeLoc));
		verify(ledger, times(1)).record(any());
	}

	@Test
	@DisplayName("the actor himself is never recorded by the listener")
	void actor_notRecorded() {
		player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(argThat(e -> e.getPlayer() == actor));
	}

	@Test
	@DisplayName("an allied gang member within the radius shares")
	void allyWithinRadius_shares() {
		Player ally = player("Ally", ALLY_GANG, 36);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger).record(argThat(e -> recordsFor(e, ally, Crimes.KILL_CIVILIAN)));
	}

	@Test
	@DisplayName("a gang-less friend and a player of an unrelated gang get nothing")
	void nonCrewNearby_noShare() {
		player("Friend", -1, 1);
		player("Rival", OTHER_GANG, 1);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("a mate 17 blocks away gets nothing, one at exactly 16 shares")
	void mateOutsideRadius_noShare() {
		player("Far", OWN_GANG, 17 * 17);
		Player edge = player("Edge", OWN_GANG, 16 * 16);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(any());
		verify(ledger).record(argThat(e -> e.getPlayer() == edge));
	}

	@Test
	@DisplayName("Crew_Heat.Radius from the config decides the range")
	void radiusFromConfig() {
		when(config.getCrewHeat()).thenReturn(new CrewHeatSettings(true, 5.0, Set.of()));
		player("Beyond", OWN_GANG, 30);
		Player inside = player("Inside", OWN_GANG, 20);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(any());
		verify(ledger).record(argThat(e -> e.getPlayer() == inside));
	}

	@Test
	@DisplayName("a handcuffed or jailed mate and a downed mate are skipped, a free mate beside them shares")
	void restrainedOrDownedMate_skipped() {
		Player cuffed = player("Cuffed", OWN_GANG, 4);
		Player hurt   = player("Hurt", OWN_GANG, 4);
		Player free   = player("Free", OWN_GANG, 4);
		when(detainment.isRestrained(cuffed)).thenReturn(true);
		DownedPlayerRegistry.add(hurt.getUniqueId());
		downed.add(hurt.getUniqueId());

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(any());
		verify(ledger).record(argThat(e -> e.getPlayer() == free));
	}

	@Test
	@DisplayName("an NPC standing in the crew's gang is skipped")
	void npcMate_skipped() {
		Player npc = player("Npc", OWN_GANG, 4);
		npcSupport.when(() -> NpcSupport.isNpc(npc)).thenReturn(true);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("a mate defending his own contested turf at the spot is skipped, an ally who is not the owner shares")
	void mateDefendingOwnTurf_skipped() {
		Turf             turf  = mock(Turf.class);
		TurfRuntimeState state = mock(TurfRuntimeState.class);
		when(turfs.findAt(crimeLoc)).thenReturn(turf);
		when(turf.getId()).thenReturn(7);
		when(turfs.getRuntimeState(7)).thenReturn(state);
		when(state.getState()).thenReturn(TurfState.CONTESTING);
		when(turf.getOwnerGangId()).thenReturn(ALLY_GANG);
		Player owner = player("Owner", ALLY_GANG, 4);
		Player mate  = player("Mate", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(argThat(e -> e.getPlayer() == owner));
		verify(ledger).record(argThat(e -> e.getPlayer() == mate));
	}

	@Test
	@DisplayName("the listener never fires an event: the PluginManager is not touched, so shared heat is never re-shared")
	void neverRefired() {
		player("Alex", OWN_GANG, 4);
		player("Ally", ALLY_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(pluginManager, never()).callEvent(any());
		bukkit.verify(Bukkit::getPluginManager, never());
		verify(ledger, times(2)).record(any());
	}

	@Test
	@DisplayName("Crew_Heat.Enable false shares nothing")
	void disabled_noop() {
		when(config.getCrewHeat()).thenReturn(new CrewHeatSettings(false, 16, Set.of()));
		player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("a reloaded config applies to the next crime (settings are read per event)")
	void reload_appliesToNextCrime() {
		player("Alex", OWN_GANG, 4);
		share(Crimes.KILL_CIVILIAN);
		when(config.getCrewHeat()).thenReturn(new CrewHeatSettings(false, 16, Set.of()));
		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(any());
	}

	@Test
	@DisplayName("with no gang module installed nobody is a mate")
	void noGangModule_noShare() {
		listener = new CrewHeatListener(ledger, new GangMembership(), turfs, detainment, config, messages);
		player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("an actor in no gang shares with nobody, even with a gang-less player beside him")
	void actorWithoutGang_noShare() {
		gangOf.put(actor.getUniqueId(), -1);
		player("Loner", -1, 1);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("a crime in Exclude_Crimes is never shared (case-insensitive), another crime still is")
	void excludedCrime_notShared() {
		when(config.getCrewHeat()).thenReturn(new CrewHeatSettings(true, 16, Set.of("trespass_restricted")));
		Player mate = player("Alex", OWN_GANG, 4);

		share("Trespass_Restricted");
		verify(ledger, never()).record(any());

		share(Crimes.KILL_CIVILIAN);
		verify(ledger).record(argThat(e -> recordsFor(e, mate, Crimes.KILL_CIVILIAN)));
	}

	@Test
	@DisplayName("the shipped default excludes Trespass_Restricted, Evading_Police and Brandish_Near_Cop (C-12)")
	void shippedDefault_excludesThreeCrimes() {
		player("Alex", OWN_GANG, 4);

		share("Trespass_Restricted");
		share("Evading_Police");
		share("Brandish_Near_Cop");
		verify(ledger, never()).record(any());

		share(Crimes.KILL_COP);
		verify(ledger, times(1)).record(any());
	}

	@Test
	@DisplayName("an excluded id that is no crime at all is ignored silently, nothing throws")
	void unknownExcludedId_isHarmless() {
		when(config.getCrewHeat()).thenReturn(new CrewHeatSettings(true, 16, Set.of("not_a_crime")));
		player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_COP);

		verify(ledger, times(1)).record(any());
	}

	@Test
	@DisplayName("a crime with no world shares nothing and does not throw")
	void nullWorld_noShare() {
		when(crimeLoc.getWorld()).thenReturn(null);
		player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, never()).record(any());
	}

	@Test
	@DisplayName("only players online in the crime's world are candidates")
	void offlineOrOtherWorldMate_notShared() {
		Player here = player("Here", OWN_GANG, 4);
		// a mate in another world or offline is simply not in this world's player list
		Player away = mock(Player.class);
		when(away.getUniqueId()).thenReturn(UUID.randomUUID());
		gangOf.put(away.getUniqueId(), OWN_GANG);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger, times(1)).record(any());
		verify(ledger).record(argThat(e -> e.getPlayer() == here));
	}

	@Test
	@DisplayName("the mate's record copies the actor's seenByCop flag and witness count")
	void mateRecord_copiesSeenByCopAndWitnesses() {
		player("Alex", OWN_GANG, 4);
		ArgumentCaptor<CrimeCommittedEvent> captor = ArgumentCaptor.forClass(CrimeCommittedEvent.class);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger).record(captor.capture());
		assertTrue(captor.getValue().isSeenByCop());
		assertEquals(3, captor.getValue().getWitnesses());
	}

	@Test
	@DisplayName("each mate who was recorded gets one chat line naming the actor and the crime")
	void mateGetsOneChatLine_namingActorAndCrime() {
		Player mate = player("Alex", OWN_GANG, 4);

		share(Crimes.KILL_CIVILIAN);

		@SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
		verify(messages).format(eq(WantedMessages.Key.CREW_HEAT_SHARED), values.capture());
		assertEquals("Steve", values.getValue().get("player"));
		assertEquals("Kill Civilian", values.getValue().get("crime"));
		verify(mate, times(1)).sendMessage("shared-line");
		verify(actor, never()).sendMessage(any(String.class));
	}

	@Test
	@DisplayName("a mate whose ledger ignored the crime (heat 0) gets no chat line")
	void ledgerIgnoredCrime_noChat() {
		Player mate = player("Alex", OWN_GANG, 4);
		when(ledger.record(any())).thenReturn(0.0);

		share(Crimes.KILL_CIVILIAN);

		verify(ledger).record(any());
		verify(mate, never()).sendMessage(any(String.class));
	}

	@Test
	@DisplayName("the handler runs at MONITOR and skips cancelled events (witness-gated crimes share on the 911 recommit)")
	void cancelledCrime_notShared() throws Exception {
		Method       m       = CrewHeatListener.class.getMethod("onCrime", CrimeCommittedEvent.class);
		EventHandler handler = m.getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertTrue(handler.ignoreCancelled());
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertNotNull(CrewHeatListener.class.getAnnotation(ListenerHandler.class));
	}

	// -- real HeatLedger: the mate's own multipliers ------------------------------------------------------------

	private HeatLedger realLedger(AtomicLong clock, boolean contested, AtomicInteger triggers) {
		@SuppressWarnings("unchecked") UserManager<Player> users = mock(UserManager.class);
		for (Player p : inWorld) {
			Wanted wanted = new Wanted(null, 1, 5);
			wanteds.put(p, wanted);
			@SuppressWarnings("unchecked") User<Player> user = mock(User.class);
			when(user.getWanted()).thenReturn(wanted);
			when(users.getUser(p)).thenReturn(user);
		}
		HeatLedger real = new HeatLedger(config, users, mock(CrimeService.class), p -> false, l -> contested,
		                                 () -> 10, clock::get);
		real.setStarTrigger(p -> {
			triggers.incrementAndGet();
			Wanted w = wanteds.get(p);
			w.setLevel(w.getLevel() + 1);
		});
		return real;
	}

	@Test
	@DisplayName("with a real ledger the mate's heat uses HIS streak and turf multipliers, not the actor's")
	void mateHeat_usesMateOwnMultipliers() {
		HeatSettings  heat     = HeatSettings.DEFAULT;
		Player        mate     = player("Alex", OWN_GANG, 4);
		AtomicLong    clock    = new AtomicLong(1_000_000L);
		AtomicInteger triggers = new AtomicInteger();
		HeatLedger    real     = realLedger(clock, true, triggers);
		listener = new CrewHeatListener(real, gangs, turfs, detainment, config, messages);

		// the actor already has a streak going; the mate has none
		real.record(new CrimeCommittedEvent(actor, Crimes.KILL_COP, crimeLoc, false, 0));
		clock.addAndGet(1_000);
		double actorOwn   = real.record(new CrimeCommittedEvent(actor, Crimes.KILL_PLAYER, crimeLoc, true, 3));
		double heatBefore = real.heatOf(actor.getUniqueId());

		share(Crimes.KILL_PLAYER);

		double expected = heat.weightOf(Crimes.KILL_PLAYER) * heat.seenByCopMultiplier() * heat.turfWarMultiplier();
		assertEquals(expected, real.heatOf(mate.getUniqueId()), 1e-9);
		assertNotEquals(actorOwn, real.heatOf(mate.getUniqueId()));
		assertEquals(Crimes.KILL_PLAYER, real.lastCrime(mate.getUniqueId()).crimeId());
		assertEquals(heatBefore, real.heatOf(actor.getUniqueId()), 1e-9);
	}

	@Test
	@DisplayName("a mate with his own running streak gets his streak bonus on the shared crime")
	void mateWithOwnStreak_getsHisBonus() {
		HeatSettings heat  = HeatSettings.DEFAULT;
		Player       mate  = player("Alex", OWN_GANG, 4);
		AtomicLong   clock = new AtomicLong(1_000_000L);
		HeatLedger   real  = realLedger(clock, false, new AtomicInteger());
		listener = new CrewHeatListener(real, gangs, turfs, detainment, config, messages);

		real.record(new CrimeCommittedEvent(mate, Crimes.KILL_COP, crimeLoc, false, 0));
		clock.addAndGet(1_000);
		double first = real.heatOf(mate.getUniqueId());

		listener.onCrime(new CrimeCommittedEvent(actor, Crimes.KILL_COP, crimeLoc, false, 0));

		assertEquals(first + heat.weightOf(Crimes.KILL_COP) * heat.streakBonus(), real.heatOf(mate.getUniqueId()),
		             1e-9);
	}

	@Test
	@DisplayName("each mate gets his own stars: two mates, two star triggers, the actor untouched")
	void eachMateGetsOwnStars() {
		Player a = player("A", OWN_GANG, 4);
		Player b = player("B", ALLY_GANG, 4);
		HeatLedger real = realLedger(new AtomicLong(1_000_000L), false, new AtomicInteger());
		listener = new CrewHeatListener(real, gangs, turfs, detainment, config, messages);

		listener.onCrime(new CrimeCommittedEvent(actor, Crimes.KILL_COP, crimeLoc, false, 0));

		assertTrue(wanteds.get(a).getLevel() >= 1);
		assertTrue(wanteds.get(b).getLevel() >= 1);
		assertEquals(0, wanteds.get(actor).getLevel(), "the listener itself never records the actor");
	}

	/** Bukkit's dispatch for one event: handlers by priority (stable), cancelled events skip ignoreCancelled ones. */
	private static void dispatch(CrimeCommittedEvent event, Object... listeners) throws ReflectiveOperationException {
		List<Object> order = new ArrayList<>(List.of(listeners));
		order.sort(java.util.Comparator.comparingInt(l -> priorityOf(l).getSlot()));
		for (Object l : order) {
			EventHandler h = l.getClass().getMethod("onCrime", CrimeCommittedEvent.class).getAnnotation(EventHandler.class);
			if (event.isCancelled() && h.ignoreCancelled()) {
				continue;
			}
			l.getClass().getMethod("onCrime", CrimeCommittedEvent.class).invoke(l, event);
		}
	}

	private static EventPriority priorityOf(Object l) {
		try {
			return l.getClass().getMethod("onCrime", CrimeCommittedEvent.class).getAnnotation(EventHandler.class).priority();
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("cnc-integ INT-2: a witness-gated (cancelled) unseen crime is not shared; the 911 recommit shares it exactly once")
	void integ_gatedCrimeNotSharedUntilRecommit() throws ReflectiveOperationException {
		Player mate = player("Alex", OWN_GANG, 25);
		npcSupport.when(NpcSupport::available).thenReturn(true);
		org.luckyraven.gangland.civilians.witness.WitnessService witnesses =
				mock(org.luckyraven.gangland.civilians.witness.WitnessService.class);
		when(witnesses.witness(any(), any(), any())).thenReturn(1);
		when(config.getWitnesses()).thenReturn(org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings.DEFAULT);
		WitnessGateListener gate = new WitnessGateListener(config, witnesses,
		                                                   mock(org.luckyraven.gangland.copsncrooks.npc.police.CopManager.class));

		CrimeCommittedEvent gated = new CrimeCommittedEvent(actor, Crimes.CAR_THEFT, crimeLoc, false, 0);
		dispatch(gated, listener, gate);

		assertTrue(gated.isCancelled());
		verify(ledger, never()).record(any());

		CrimeCommittedEvent recommit = new CrimeCommittedEvent(actor, Crimes.CAR_THEFT, crimeLoc, false, 1);
		dispatch(recommit, listener, gate);

		verify(ledger, times(1)).record(argThat(e -> recordsFor(e, mate, Crimes.CAR_THEFT)));
		verify(ledger, times(1)).record(any());
	}
}
