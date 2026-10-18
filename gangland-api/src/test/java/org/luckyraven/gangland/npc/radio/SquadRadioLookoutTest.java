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
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** 2.7 (lane look): RadioVoice.relaysTo lets a delivered hearer pass a line on to the hunted player, once. */
@DisplayName("SquadRadio - RadioVoice.relaysTo (api 2.7)")
class SquadRadioLookoutTest {

	private static final String RELAY_PREFIX = "[Lookout Mate] ";

	private World                     world;
	private AtomicLong                clock;
	private Player                    hunted;
	private Set<Player>               relayers;
	private List<Player>              asked;
	private Map<String, List<String>> pools;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		world    = mock(World.class);
		clock    = new AtomicLong(1_000_000L);
		relayers = new HashSet<>();
		asked    = new ArrayList<>();
		pools    = new HashMap<>(Map.of(
				"Contact", List.of("Contact!"),
				"Check_Fire", List.of("Check your fire!"),
				"Reloading", List.of("Reloading!"),
				"Format", List.of("[RADIO] %unit%: %line%"),
				"Lookout_Format", List.of("[Lookout %name%] %line%")));
		hunted = player("Runner", 150);
	}

	private SquadRadio radio() {
		// Range 20, Target_Range 40, squad gap 0, player gap 5000 ms; Contact is priority.
		RadioSettings settings = new RadioSettings(true, 20.0, 40.0, 0, 5000, 25, 2, Map.of(), Set.of("Contact"), null,
		                                           1f, 1f);
		return new SquadRadio(() -> settings, key -> pools.getOrDefault(key, List.of()), clock::get, () -> 0.0,
		                      (runnable, delay) -> {
		                      });
	}

	private RadioVoice lookoutVoice(LivingEntity target) {
		return new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return "UNIT";
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return target;
			}

			@Override
			public boolean relaysTo(Player hearer, NpcSquad squad) {
				asked.add(hearer);
				return relayers.contains(hearer);
			}
		};
	}

	private RadioVoice plainVoice(LivingEntity target) {
		return new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return "UNIT";
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return target;
			}
		};
	}

	private Player player(String name, double x) {
		Player p = mock(Player.class);
		when(p.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(p.getUniqueId()).thenReturn(UUID.randomUUID());
		when(p.getName()).thenReturn(name);
		when(p.isOnline()).thenReturn(true);
		when(p.hasMetadata("NPC")).thenReturn(false);
		return p;
	}

	private AbstractNpc member() {
		AbstractNpc  npc    = mock(AbstractNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(npc.getEntity()).thenReturn(entity);
		when(npc.isValid()).thenReturn(true);
		return npc;
	}

	private boolean say(SquadRadio radio, RadioVoice voice, NpcSquad squad, AbstractNpc m, String key) {
		return radio.say(squad, voice, m.getEntity(), "UNIT", key, "Format", new Location(world, 0, 64, 0), null,
		                 Map.of());
	}

	private NpcSquad squadOf(AbstractNpc m) {
		NpcSquad squad = new NpcSquad();
		squad.add(m);
		return squad;
	}

	private List<String> received(Player p, int times) {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(p, times(times)).sendMessage(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("a hearer who relays passes the line to the far hunted player once, with his name and the heard text")
	void relayReachesHuntedOnce() {
		AbstractNpc m    = member();
		Player      mate = player("Mate", 5);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, hunted));

		assertTrue(say(radio(), lookoutVoice(hunted), squadOf(m), m, "Contact"));

		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 1));
		verify(mate, times(1)).sendMessage("[RADIO] UNIT: Contact!");
	}

	@Test
	@DisplayName("a hunted player who heard the line directly (inside Target_Range) gets that one message and no relay")
	void huntedWhoHeardDirectlyGetsNoRelay() {
		AbstractNpc m      = member();
		Player      mate   = player("Mate", 5);
		Player      direct = player("Runner", 30);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, direct));

		say(radio(), lookoutVoice(direct), squadOf(m), m, "Contact");

		assertEquals(List.of("[RADIO] UNIT: Contact!"), received(direct, 1));
	}

	@Test
	@DisplayName("a voice without a relaysTo override never relays")
	void defaultVoiceNeverRelays() {
		AbstractNpc m    = member();
		Player      mate = player("Mate", 5);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, hunted));

		assertTrue(say(radio(), plainVoice(hunted), squadOf(m), m, "Contact"));

		verify(hunted, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the hunted player's own gap applies: a non-priority relay inside it is dropped, a priority line relays")
	void relayRespectsHuntedGap() {
		AbstractNpc m    = member();
		Player      mate = player("Mate", 300);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, hunted));
		SquadRadio radio = radio();
		RadioVoice voice = lookoutVoice(hunted);
		NpcSquad   squad = squadOf(m);

		// line 1: only the hunted player hears it (inside Target_Range), which starts his player gap
		when(hunted.getLocation()).thenReturn(new Location(world, 30, 64, 0));
		assertTrue(say(radio, voice, squad, m, "Reloading"));
		verify(hunted, times(1)).sendMessage(anyString());

		// line 2: the mate is now in range, the hunted player is far and still inside his gap
		clock.addAndGet(100);
		when(hunted.getLocation()).thenReturn(new Location(world, 150, 64, 0));
		when(mate.getLocation()).thenReturn(new Location(world, 5, 64, 0));
		assertTrue(say(radio, voice, squad, m, "Check_Fire"));
		verify(mate, times(1)).sendMessage("[RADIO] UNIT: Check your fire!");
		verify(hunted, times(1)).sendMessage(anyString());

		// line 3: a priority line bypasses the gap
		clock.addAndGet(100);
		assertTrue(say(radio, voice, squad, m, "Contact"));
		assertEquals(List.of("[RADIO] UNIT: Reloading!", RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 2));
	}

	@Test
	@DisplayName("two hearers who both relay still give the hunted player one relay, named after the first")
	void twoLookoutsStillOneRelay() {
		AbstractNpc m     = member();
		Player      mateA = player("Mate", 5);
		Player      mateB = player("Other", 6);
		relayers.add(mateA);
		relayers.add(mateB);
		when(world.getPlayers()).thenReturn(List.of(mateA, mateB, hunted));

		say(radio(), lookoutVoice(hunted), squadOf(m), m, "Contact");

		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 1));
		assertEquals(List.of(mateA), asked, "stops asking after the first true");
	}

	@Test
	@DisplayName("with no Lookout_Format pool the relay falls back to the lookout's name in brackets, then the line")
	void missingFormatUsesFallback() {
		pools.remove("Lookout_Format");
		AbstractNpc m    = member();
		Player      mate = player("Mate", 5);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, hunted));

		say(radio(), lookoutVoice(hunted), squadOf(m), m, "Contact");

		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 1));
	}

	@Test
	@DisplayName("relaysTo is only asked for hearers who were delivered the line")
	void relayAskedOnlyForDeliveredHearers() {
		AbstractNpc m    = member();
		Player      far  = player("Far", 150);
		Player      mate = player("Mate", 5);
		relayers.add(far);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(far, mate, hunted));

		assertTrue(say(radio(), lookoutVoice(hunted), squadOf(m), m, "Contact"));

		assertEquals(List.of(mate), asked);
		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 1));
	}

	@Test
	@DisplayName("an offline hunted player is never relayed to; a squad with no hunted player relays to no one")
	void offlineOrAbsentHuntedGetsNothing() {
		AbstractNpc m    = member();
		Player      mate = player("Mate", 5);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(mate, hunted));
		NpcSquad squad = squadOf(m);

		when(hunted.isOnline()).thenReturn(false);
		assertTrue(say(radio(), lookoutVoice(hunted), squad, m, "Contact"));
		verify(hunted, never()).sendMessage(anyString());

		clock.addAndGet(10_000);
		assertTrue(say(radio(), lookoutVoice(null), squad, m, "Reloading"));
		verify(hunted, never()).sendMessage(anyString());

		clock.addAndGet(10_000);
		when(hunted.isOnline()).thenReturn(true);
		assertTrue(say(radio(), lookoutVoice(hunted), squad, m, "Check_Fire"));
		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Check your fire!"), received(hunted, 1));
	}

	@Test
	@DisplayName("an NPC-flagged hearer is skipped and so never asked to relay; a real mate beside him still relays")
	void npcHearerNeverRelays() {
		AbstractNpc m    = member();
		Player      npc  = player("Mate", 5);
		Player      mate = player("Mate", 6);
		when(npc.hasMetadata("NPC")).thenReturn(true);
		relayers.add(npc);
		relayers.add(mate);
		when(world.getPlayers()).thenReturn(List.of(npc, mate, hunted));

		say(radio(), lookoutVoice(hunted), squadOf(m), m, "Contact");

		assertEquals(List.of(mate), asked);
		assertEquals(List.of(RELAY_PREFIX + "[RADIO] UNIT: Contact!"), received(hunted, 1));
	}
}
