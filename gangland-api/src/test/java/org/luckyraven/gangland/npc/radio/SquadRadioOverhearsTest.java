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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 2.7: RadioVoice.overhears widens who hears a line; range, squad gap and player gap rules otherwise unchanged. */
@DisplayName("SquadRadio - RadioVoice.overhears (api 2.7)")
class SquadRadioOverhearsTest {

	private static final Map<String, List<String>> POOLS = Map.of(
			"Contact", List.of("Contact!"),
			"Check_Fire", List.of("Check your fire!"),
			"Format", List.of("[RADIO] %unit%: %line%"));

	private World       world;
	private AtomicLong  clock;
	private SquadRadio  radio;
	private Set<Player> overhearing;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		world       = mock(World.class);
		clock       = new AtomicLong(1_000_000L);
		overhearing = new HashSet<>();
		// Range 20, Target_Range 40, squad gap 0, player gap 5000 ms; Contact is priority.
		RadioSettings settings = new RadioSettings(true, 20.0, 40.0, 0, 5000, 25, 2, Map.of(), Set.of("Contact"), null,
		                                           1f, 1f);
		radio = new SquadRadio(() -> settings, key -> POOLS.getOrDefault(key, List.of()), clock::get, () -> 0.0,
		                       (runnable, delay) -> {
		                       });
	}

	private RadioVoice overhearingVoice() {
		return new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return "UNIT";
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return null;
			}

			@Override
			public boolean overhears(Player listener, NpcSquad squad) {
				return overhearing.contains(listener);
			}
		};
	}

	private RadioVoice plainVoice() {
		return new RadioVoice() {
			@Override
			public String callsign(AbstractNpc npc) {
				return "UNIT";
			}

			@Override
			public LivingEntity hunted(NpcSquad squad) {
				return null;
			}
		};
	}

	private Player player(double x) {
		Player p = mock(Player.class);
		when(p.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(p.getUniqueId()).thenReturn(UUID.randomUUID());
		when(p.getName()).thenReturn("P");
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

	private boolean say(RadioVoice voice, NpcSquad squad, AbstractNpc m, String key) {
		return radio.say(squad, voice, m.getEntity(), "UNIT", key, "Format", new Location(world, 0, 64, 0), null,
		                 Map.of());
	}

	@Test
	@DisplayName("a player 150 blocks away hears the line when the voice says it overhears")
	void overhearingPlayerOutOfRangeHearsLine() {
		AbstractNpc m   = member();
		Player      far = player(150);
		overhearing.add(far);
		when(world.getPlayers()).thenReturn(List.of(far));
		NpcSquad squad = new NpcSquad();
		squad.add(m);

		assertTrue(say(overhearingVoice(), squad, m, "Contact"));
		verify(far).sendMessage(anyString());
	}

	@Test
	@DisplayName("a voice without an overhears override changes nothing: the far player is not delivered")
	void overhearsDefaultsFalse() {
		AbstractNpc m   = member();
		Player      far = player(150);
		when(world.getPlayers()).thenReturn(List.of(far));
		NpcSquad squad = new NpcSquad();
		squad.add(m);

		assertFalse(say(plainVoice(), squad, m, "Contact"));
		verify(far, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a far player the voice does not name still hears nothing, an in-range one still hears")
	void onlyNamedListenersWiden() {
		AbstractNpc m     = member();
		Player      far   = player(150);
		Player      other = player(160);
		Player      near  = player(5);
		overhearing.add(far);
		when(world.getPlayers()).thenReturn(List.of(far, other, near));
		NpcSquad squad = new NpcSquad();
		squad.add(m);

		assertTrue(say(overhearingVoice(), squad, m, "Contact"));
		verify(far).sendMessage(anyString());
		verify(other, never()).sendMessage(anyString());
		verify(near).sendMessage(anyString());
	}

	@Test
	@DisplayName("an overhearing player is not told twice for one line (in range and overhearing counts once)")
	void inRangeAndOverhearing_deliveredOnce() {
		AbstractNpc m    = member();
		Player      near = player(5);
		overhearing.add(near);
		when(world.getPlayers()).thenReturn(List.of(near));
		NpcSquad squad = new NpcSquad();
		squad.add(m);

		assertTrue(say(overhearingVoice(), squad, m, "Contact"));
		verify(near, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("two non-priority lines inside the player gap reach an overhearing far player once")
	void overheardLineStillObeysPlayerGap() {
		AbstractNpc m   = member();
		Player      far = player(150);
		overhearing.add(far);
		when(world.getPlayers()).thenReturn(List.of(far));
		NpcSquad squad = new NpcSquad();
		squad.add(m);
		RadioVoice voice = overhearingVoice();

		assertTrue(say(voice, squad, m, "Check_Fire"));
		clock.addAndGet(100);
		say(voice, squad, m, "Check_Fire");

		verify(far, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("a priority line bypasses the player gap for an overhearing player")
	void priorityBypassesGapForOverhearer() {
		AbstractNpc m   = member();
		Player      far = player(150);
		overhearing.add(far);
		when(world.getPlayers()).thenReturn(List.of(far));
		NpcSquad squad = new NpcSquad();
		squad.add(m);
		RadioVoice voice = overhearingVoice();

		assertTrue(say(voice, squad, m, "Check_Fire"));
		clock.addAndGet(100);
		assertTrue(say(voice, squad, m, "Contact"));

		verify(far, times(2)).sendMessage(anyString());
	}

	@Test
	@DisplayName("an NPC-flagged player is skipped even when the voice says it overhears")
	void npcPlayerSkippedEvenIfOverhearing() {
		AbstractNpc m   = member();
		Player      npc = player(150);
		when(npc.hasMetadata("NPC")).thenReturn(true);
		overhearing.add(npc);
		when(world.getPlayers()).thenReturn(List.of(npc));
		NpcSquad squad = new NpcSquad();
		squad.add(m);

		assertFalse(say(overhearingVoice(), squad, m, "Contact"));
		verify(npc, never()).sendMessage(anyString());
	}
}
