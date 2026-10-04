package org.luckyraven.gangland.npc.radio;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SquadRadio - range-limited, throttled delivery; acks only after a delivered order")
class SquadRadioTest {

	private static final Map<String, List<String>> LINE_POOLS = Map.ofEntries(
			Map.entry("Contact", List.of("Contact! %distance% blocks %direction%!")),
			Map.entry("Man_Down", List.of("Officer down! %member% is down!")),
			Map.entry("Leader_Down", List.of("%member% is down!")),
			Map.entry("Flank_Left", List.of("%member%, take his %side%!")),
			Map.entry("Flank_Right", List.of("%member%, take his %side%!")),
			Map.entry("Ack", List.of("Copy.")),
			Map.entry("Check_Fire", List.of("Check your fire!")),
			Map.entry("Hit", List.of("I'm hit!")),
			Map.entry("Search", List.of("Spread out, find him!", "Sweep the area!")),
			Map.entry("Reposition", List.of()),
			Map.entry("Route", List.of("Cutting round!")),
			Map.entry("Route_High", List.of("He's up high!")),
			Map.entry("Climb", List.of("Taking the ladder!")),
			Map.entry("Format", List.of("[RADIO] %unit%: %line%")),
			Map.entry("Compass",
			         List.of("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")),
			Map.entry("Sides", List.of("front", "left", "right", "behind")));

	private World                     world;
	private AtomicLong                clock;
	private double[]                  rngValue;
	private List<Object[]>            scheduled;
	private Map<AbstractNpc, String>  callsigns;
	private LivingEntity              huntedPlayer;
	private RadioSettings             settings;
	private RadioLines                lines;
	private SquadRadio                radio;
	private RadioVoice                voice;

	private static RadioSettings defaultSettings() {
		// soundName null: playing a real XSound needs a live Bukkit.getServer(), which a headless unit test has
		// none of. Sound playback isn't part of this suite's contract (it's a one-line pass-through to SoundEffect).
		return new RadioSettings(true, 20.0, 40.0, 1500, 1000, 25, 2, Map.of("Contact", 8000L),
		                         Set.of("Contact", "Man_Down", "Leader_Down"), null, 0.4f, 1.0f);
	}

	// Settings' ~200 fields are process-wide statics with no test reset hook (documentation/TESTING.md §4): an
	// uninitialized Money_Symbol NPEs every GanglandChatUtil.color(...) call (SquadRadio.speak wraps every line
	// through it), not just ones using %money_symbol%. Poke the one field this suite needs directly rather than
	// pulling in keystone-testkit's full FileHandler/FileManager Settings.initialize() fixture for it.
	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		world     = mock(World.class);
		clock     = new AtomicLong(1_000_000L);
		rngValue  = new double[]{0.0};
		scheduled = new ArrayList<>();
		callsigns = new HashMap<>();
		huntedPlayer = null;
		settings  = defaultSettings();
		lines     = key -> LINE_POOLS.getOrDefault(key, List.of());

		radio = new SquadRadio(() -> settings, lines, clock::get, () -> rngValue[0],
		                       (runnable, delayTicks) -> scheduled.add(new Object[]{runnable, delayTicks}));

		voice = new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return callsigns.getOrDefault(npc, "UNIT");
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return huntedPlayer;
			}
		};
	}

	private Player newPlayer(double x, double y, double z) {
		Player   p   = mock(Player.class);
		Location loc = new Location(world, x, y, z);
		when(p.getLocation()).thenReturn(loc);
		when(p.getUniqueId()).thenReturn(UUID.randomUUID());
		when(p.getName()).thenReturn("Player"); // %target% needs a non-null name when this player is the hunted one
		when(p.hasMetadata("NPC")).thenReturn(false);
		return p;
	}

	private AbstractNpc newMember(double x, double y, double z, String callsign) {
		AbstractNpc  npc    = mock(AbstractNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, x, y, z));
		when(npc.getEntity()).thenReturn(entity);
		when(npc.isValid()).thenReturn(true);
		callsigns.put(npc, callsign);
		return npc;
	}

	@Test
	@DisplayName("a player inside Range hears; one beyond it, or in another world, never does")
	void rangeFilter_hearsAt20_notAt40_neverOtherWorld() {
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      near   = newPlayer(10, 64, 0);
		Player      far    = newPlayer(35, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(near, far));

		NpcSquad squad = new NpcSquad();
		squad.add(member);

		boolean delivered = radio.say(squad, voice, member.getEntity(), "SWAT-1", "Contact", "Format",
		                              new Location(world, 10, 64, 0), null, Map.of());

		assertTrue(delivered);
		verify(near).sendMessage(anyString());
		verify(far, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the hunted player hears from Target_Range, beyond the ordinary Range")
	void huntedPlayer_hearsWithinTargetRange() {
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      hunted = newPlayer(35, 64, 0); // beyond Range (20) but inside Target_Range (40)
		huntedPlayer = hunted;
		when(world.getPlayers()).thenReturn(List.of(hunted));

		NpcSquad squad = new NpcSquad();
		squad.add(member);

		boolean delivered = radio.say(squad, voice, member.getEntity(), "SWAT-1", "Contact", "Format",
		                              new Location(world, 0, 64, 0), null, Map.of());

		assertTrue(delivered);
		verify(hunted).sendMessage(anyString());
	}

	@Test
	@DisplayName("a player near the addressee (but far from the speaker) also hears")
	void addresseeNeighbourhood_alsoHears() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(50, 64, 0, "SWAT-2");
		Player      near   = newPlayer(52, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(near));

		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		boolean delivered = radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Flank_Left", "Format",
		                              new Location(world, 0, 64, 0), member.getEntity(), Map.of("member", "SWAT-2"));

		assertTrue(delivered);
		verify(near).sendMessage(anyString());
	}

	@Test
	@DisplayName("a second non-priority line from the same squad is dropped inside the squad gap; a priority line bypasses it")
	void squadGap_dropsSecondNonPriorityLine_priorityBypasses() {
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(5, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));

		NpcSquad squad = new NpcSquad();
		squad.add(member);
		Location where = new Location(world, 0, 64, 0);

		assertTrue(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Check_Fire", "Format", where, null,
		                     Map.of()));
		clock.addAndGet(100);
		assertFalse(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Check_Fire", "Format", where, null,
		                      Map.of()));
		assertTrue(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Contact", "Format", where, null, Map.of()));
	}

	@Test
	@DisplayName("a key's own cooldown applies even to a priority line and to Ack")
	void keyCooldown_appliesToPriorityAndAck() {
		settings = new RadioSettings(true, 20, 40, 0, 0, 25, 2, Map.of("Contact", 5000L, "Ack", 5000L),
		                             Set.of("Contact"), null, 1f, 1f);
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(5, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		Location where = new Location(world, 0, 64, 0);

		NpcSquad contactSquad = new NpcSquad();
		contactSquad.add(member);
		assertTrue(radio.say(contactSquad, voice, member.getEntity(), "SWAT-1", "Contact", "Format", where, null,
		                     Map.of()));
		clock.addAndGet(100);
		assertFalse(radio.say(contactSquad, voice, member.getEntity(), "SWAT-1", "Contact", "Format", where, null,
		                      Map.of()));

		NpcSquad ackSquad = new NpcSquad();
		ackSquad.add(member);
		assertTrue(radio.say(ackSquad, voice, member.getEntity(), "SWAT-1", "Ack", "Format", where, null, Map.of()));
		clock.addAndGet(100);
		assertFalse(radio.say(ackSquad, voice, member.getEntity(), "SWAT-1", "Ack", "Format", where, null, Map.of()));
	}

	@Test
	@DisplayName("the player gap holds across two different squads; a priority line bypasses it")
	void playerGap_holdsAcrossTwoSquads_priorityBypasses() {
		settings = new RadioSettings(true, 20, 40, 0, 5000, 25, 2, Map.of(), Set.of("Contact"), null, 1f, 1f);
		AbstractNpc m1 = newMember(0, 64, 0, "A-1");
		AbstractNpc m2 = newMember(0, 64, 0, "B-1");
		Player      p  = newPlayer(2, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		Location where = new Location(world, 0, 64, 0);

		NpcSquad squad1 = new NpcSquad();
		squad1.add(m1);
		NpcSquad squad2 = new NpcSquad();
		squad2.add(m2);

		assertTrue(radio.say(squad1, voice, m1.getEntity(), "A-1", "Check_Fire", "Format", where, null, Map.of()));
		clock.addAndGet(100);
		assertFalse(radio.say(squad2, voice, m2.getEntity(), "B-1", "Check_Fire", "Format", where, null, Map.of()));
		assertTrue(radio.say(squad2, voice, m2.getEntity(), "B-1", "Contact", "Format", where, null, Map.of()));
	}

	@Test
	@DisplayName("an empty line pool is silent and consumes no throttle")
	void emptyLines_silent_andThrottleNotConsumed() {
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(5, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(member);
		Location where = new Location(world, 0, 64, 0);

		assertFalse(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Reposition", "Format", where, null,
		                      Map.of()));
		verify(p, never()).sendMessage(anyString());
		assertTrue(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Check_Fire", "Format", where, null,
		                     Map.of()));
	}

	@Test
	@DisplayName("Enabled: false silences every line")
	void disabled_noOutput() {
		settings = new RadioSettings(false, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(5, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(member);

		assertFalse(radio.say(squad, voice, member.getEntity(), "SWAT-1", "Contact", "Format",
		                      new Location(world, 0, 64, 0), null, Map.of()));
		verify(p, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a delivered order schedules its ack, which fires despite the squad gap the order itself started")
	void orderDelivered_ackDelivered_despiteSquadGap() {
		settings = new RadioSettings(true, 20, 40, 1500, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		NpcSquadListener listener = radio.listener(voice);
		listener.onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));

		verify(p, times(1)).sendMessage(anyString());
		assertEquals(1, scheduled.size());
		assertEquals(25L, scheduled.get(0)[1]);

		((Runnable) scheduled.get(0)[0]).run();
		verify(p, times(2)).sendMessage(anyString());
	}

	@Test
	@DisplayName("an order throttled by the squad gap schedules no ack")
	void orderThrottled_noAckScheduled() {
		settings = new RadioSettings(true, 20, 40, 1500, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);
		Location where = new Location(world, 0, 64, 0);

		radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Check_Fire", "Format", where, null, Map.of());
		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));

		assertTrue(scheduled.isEmpty());
	}

	@Test
	@DisplayName("an order with no player in range schedules no ack")
	void orderWithNoPlayerInRange_noAckScheduled() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		when(world.getPlayers()).thenReturn(List.of());
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));

		assertTrue(scheduled.isEmpty());
	}

	@Test
	@DisplayName("a leader never orders itself: no line, no ack")
	void leaderOrdersItself_noLineNoAck() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);

		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, leader, new Location(world, 0, 64, 0));

		verify(p, never()).sendMessage(anyString());
		assertTrue(scheduled.isEmpty());
	}

	@Test
	@DisplayName("an ack is skipped when the member is no longer valid by the time the delay fires")
	void ackSkippedWhenMemberInvalidAtDelay() {
		settings = new RadioSettings(true, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));
		assertEquals(1, scheduled.size());

		when(member.isValid()).thenReturn(false);
		((Runnable) scheduled.get(0)[0]).run();

		verify(p, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("a scheduled ack reads the live settings when it fires, not the order-time snapshot")
	void ackReadsLiveSettingsAtDelay() {
		settings = new RadioSettings(true, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));
		assertEquals(1, scheduled.size());

		settings = new RadioSettings(false, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f); // reload: off
		((Runnable) scheduled.get(0)[0]).run();

		verify(p, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("MAN_DOWN is spoken by the surviving leader, naming the downed member")
	void manDown_spokenByLeader_namingDowned() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc downed = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));

		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(downed);
		squad.setListener(radio.listener(voice));

		squad.memberDown(downed);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("SWAT-2"));
	}

	@Test
	@DisplayName("LEADER_DOWN is spoken by the new leader, naming the downed leader")
	void leaderDown_spokenByNewLeader() {
		AbstractNpc leader   = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc survivor = newMember(2, 64, 0, "SWAT-2");
		Player      p        = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));

		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(survivor);
		squad.setListener(radio.listener(voice));

		squad.memberDown(leader);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("SWAT-1"));
	}

	@Test
	@DisplayName("%unit%, %distance% and %direction% are filled from the speaker and the spot")
	void placeholders_unitDistanceDirection() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);

		boolean delivered = radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Contact", "Format",
		                              new Location(world, 0, 64, -10), null, Map.of());
		assertTrue(delivered);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		String text = captor.getValue();
		assertTrue(text.contains("SWAT-1"));
		assertTrue(text.contains("10"));
		assertTrue(text.contains("north"));
	}

	@Test
	@DisplayName("%member% and %side% are filled for an order, side relative to how the hunted player faces")
	void placeholders_memberAndSide() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));

		Player huntedP = mock(Player.class);
		when(huntedP.getLocation()).thenReturn(new Location(world, 0, 64, 0, 0f, 0f)); // yaw 0 faces +z
		when(huntedP.getName()).thenReturn("Player");
		huntedPlayer = huntedP;

		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		// flank spot on the hunted's +x side, which is the hunted's LEFT at yaw 0 (pinned in RadioSidesTest).
		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 5, 64, 0));

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		String text = captor.getValue();
		assertTrue(text.contains("SWAT-2"));
		assertTrue(text.contains("left"));
	}

	@Test
	@DisplayName("the injected rng picks which line variant is spoken")
	void randomVariant_usesInjectedRng() {
		AbstractNpc member = newMember(0, 64, 0, "SWAT-1");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(member);

		rngValue[0] = 0.99;
		radio.say(squad, voice, member.getEntity(), "SWAT-1", "Search", "Format", new Location(world, 0, 64, 0), null,
		         Map.of());

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("Sweep"));
	}

	private String heardFor(NpcSquadSignal signal, double memberY, double goalY) {
		AbstractNpc member = newMember(0, memberY, 0, "SWAT-1");
		Player      p      = newPlayer(1, memberY, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(member);

		radio.listener(voice).onSignal(squad, signal, member, new Location(world, 5, goalY, 0));

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("a flat route speaks the neutral line, never the ladder or up-high ones")
	void route_flat_isNeutral() {
		String text = heardFor(NpcSquadSignal.ROUTE, 64, 64);
		assertTrue(text.contains("Cutting round"));
		assertFalse(text.contains("ladder"));
		assertFalse(text.contains("up high"));
	}

	@Test
	@DisplayName("a route to an elevated goal says he is up high; the ladder waits for CLIMB")
	void route_elevated_saysUpHigh() {
		String text = heardFor(NpcSquadSignal.ROUTE, 64, 70);
		assertTrue(text.contains("up high"));
		assertFalse(text.contains("ladder"));
	}

	@Test
	@DisplayName("CLIMB speaks the ladder line")
	void climb_saysLadder() {
		assertTrue(heardFor(NpcSquadSignal.CLIMB, 64, 70).contains("Taking the ladder"));
	}

	@Test
	@DisplayName("keyOf maps every current NpcSquadSignal value to a distinct, non-blank key")
	void keyOf_mapsEverySignal() {
		assertEquals("Contact", SquadRadio.keyOf(NpcSquadSignal.CONTACT));
		assertEquals("Contact_Lost", SquadRadio.keyOf(NpcSquadSignal.CONTACT_LOST));
		assertEquals("Flank_Left", SquadRadio.keyOf(NpcSquadSignal.FLANK_LEFT));
		assertEquals("No_Route", SquadRadio.keyOf(NpcSquadSignal.NO_ROUTE));
		assertEquals("Check_Fire", SquadRadio.keyOf(NpcSquadSignal.CHECK_FIRE));
		assertEquals("Man_Down", SquadRadio.keyOf(NpcSquadSignal.MAN_DOWN));
		assertEquals("Leader_Down", SquadRadio.keyOf(NpcSquadSignal.LEADER_DOWN));

		Set<String> mapped = new HashSet<>();
		for (NpcSquadSignal signal : NpcSquadSignal.values()) {
			String key = SquadRadio.keyOf(signal);
			assertFalse(key.isBlank());
			assertTrue(mapped.add(key), "duplicate radio key for " + signal);
		}
	}

	@Test
	@DisplayName("voice extras fill the Format wrapper too, not just the line")
	void voiceExtras_fillFormat() {
		settings = new RadioSettings(true, 20, 40, 1500, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		lines    = key -> key.equals("Format") ? List.of("[%faction%] %unit%: %line%")
		                                       : LINE_POOLS.getOrDefault(key, List.of());
		radio    = new SquadRadio(() -> settings, lines, clock::get, () -> rngValue[0], (r, d) -> {});
		RadioVoice gangVoice = new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return "G-1";
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return null;
			}

			@Override
			public Map<String, String> extras(NpcSquad squad) {
				return Map.of("faction", "Ballas");
			}
		};
		AbstractNpc member = newMember(0, 64, 0, "G-1");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(member);

		radio.say(squad, gangVoice, member.getEntity(), "G-1", "Check_Fire", "Format", null, null, Map.of());

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(p).sendMessage(text.capture());
		assertTrue(text.getValue().contains("[Ballas]"), text.getValue());
	}

	@Test
	@DisplayName("a priority Contact does not eat the squad gap: the order right after it is delivered and acked")
	void priorityContact_doesNotBlockFollowingOrder() {
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc member = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(member);

		assertTrue(radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Contact", "Format",
		                     new Location(world, 10, 64, 0), null, Map.of()));
		settings = new RadioSettings(true, 20, 40, 1500, 0, 25, 2, Map.of("Contact", 8000L), Set.of("Contact"),
		                             null, 1f, 1f); // player gap 0: this test is about the squad gap alone
		radio.listener(voice).onSignal(squad, NpcSquadSignal.FLANK_LEFT, member, new Location(world, 2, 64, 0));

		verify(p, times(2)).sendMessage(anyString());
		assertEquals(1, scheduled.size());
	}

	@Test
	@DisplayName("sayLater waits Ack_Delay_Ticks per step and stays silent for a speaker no longer valid by then")
	void sayLater_delayPerStep_silentForInvalidSpeaker() {
		settings = new RadioSettings(true, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc medic = newMember(0, 64, 0, "SWAT-2");
		Player      p     = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(medic);

		radio.sayLater(squad, voice, medic, "Check_Fire", Map.of(), 1, () -> true);
		radio.sayLater(squad, voice, medic, "Search", Map.of(), 2, () -> true);
		assertEquals(25L, scheduled.get(0)[1]);
		assertEquals(50L, scheduled.get(1)[1]);
		verify(p, never()).sendMessage(anyString());

		when(medic.isValid()).thenReturn(false);
		((Runnable) scheduled.get(0)[0]).run();
		((Runnable) scheduled.get(1)[0]).run();

		verify(p, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("sayLater stays silent when the moment passed during the delay (stillRelevant false by then)")
	void sayLater_silentWhenNoLongerRelevant() {
		settings = new RadioSettings(true, 20, 40, 0, 0, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc medic = newMember(0, 64, 0, "SWAT-2");
		Player      p     = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(medic);
		AtomicBoolean patientAlive = new AtomicBoolean(true);

		radio.sayLater(squad, voice, medic, "Search", Map.of(), 1, patientAlive::get);
		patientAlive.set(false);
		((Runnable) scheduled.get(0)[0]).run();

		verify(p, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a sayLater line gets past the squad gap another line just started, but not its own key's cooldown")
	void sayLater_bypassesSquadGap_notKeyCooldown() {
		settings = new RadioSettings(true, 20, 40, 1500, 0, 25, 2, Map.of("Search", 5000L), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc medic  = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(medic);

		assertTrue(radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Check_Fire", "Format", null, null,
		                     Map.of()));
		radio.sayLater(squad, voice, medic, "Search", Map.of(), 1, () -> true);
		radio.sayLater(squad, voice, medic, "Search", Map.of(), 2, () -> true);

		clock.addAndGet(100); // inside the 1500 ms squad gap
		((Runnable) scheduled.get(0)[0]).run();
		verify(p, times(2)).sendMessage(anyString());

		clock.addAndGet(100); // inside Search's own 5000 ms cooldown
		((Runnable) scheduled.get(1)[0]).run();
		verify(p, times(2)).sendMessage(anyString());
	}

	@Test
	@DisplayName("a sayLater line skips the player gap too: the line that set it up cannot swallow it")
	void sayLater_bypassesPlayerGap() {
		settings = new RadioSettings(true, 20, 40, 0, 5000, 25, 2, Map.of(), Set.of(), null, 1f, 1f);
		AbstractNpc leader = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc medic  = newMember(2, 64, 0, "SWAT-2");
		Player      p      = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(leader);
		squad.add(medic);

		assertTrue(radio.say(squad, voice, leader.getEntity(), "SWAT-1", "Check_Fire", "Format", null, null,
		                     Map.of()));
		radio.sayLater(squad, voice, medic, "Search", Map.of(), 1, () -> true);

		clock.addAndGet(100); // inside the 5000 ms player gap
		((Runnable) scheduled.get(0)[0]).run();

		verify(p, times(2)).sendMessage(anyString());
	}

	@Test
	@DisplayName("Hit cools down per speaker: a second wounded cop reports inside the first one's cooldown")
	void hit_cooldownIsPerSpeaker() {
		settings = new RadioSettings(true, 20, 40, 1500, 1000, 25, 2, Map.of("Hit", 5000L), Set.of("Hit"), null, 1f,
		                             1f);
		AbstractNpc a = newMember(0, 64, 0, "SWAT-1");
		AbstractNpc b = newMember(2, 64, 0, "SWAT-2");
		when(a.getEntity().getUniqueId()).thenReturn(UUID.randomUUID());
		when(b.getEntity().getUniqueId()).thenReturn(UUID.randomUUID());
		Player p = newPlayer(1, 64, 0);
		when(world.getPlayers()).thenReturn(List.of(p));
		NpcSquad squad = new NpcSquad();
		squad.add(a);
		squad.add(b);

		assertTrue(radio.say(squad, voice, a.getEntity(), "SWAT-1", "Hit", "Format", null, null, Map.of()));
		clock.addAndGet(100);
		assertTrue(radio.say(squad, voice, b.getEntity(), "SWAT-2", "Hit", "Format", null, null, Map.of()));
		clock.addAndGet(100);
		assertFalse(radio.say(squad, voice, a.getEntity(), "SWAT-1", "Hit", "Format", null, null, Map.of()));
	}
}
