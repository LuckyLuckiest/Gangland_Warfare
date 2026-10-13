package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P-SCN-2: every Interval_Seconds the nearest PURSUING cop of the wanted player's group, when farther than
 * Min_Distance, sirens at his own position. The cadence starts at the first qualifying tick.
 */
@DisplayName("SirenService")
class SirenServiceTest {

	private static final long SEC = 1000L;

	private final AtomicReference<SirenSettings> settings = new AtomicReference<>(SirenSettings.DEFAULT);
	private final long[]                         now      = {5_000_000L};
	private final List<Location>                 played   = new ArrayList<>();
	private final List<CopNpc>                   cops     = new ArrayList<>();

	private World        world;
	private Player       player;
	private CopGroup     group;
	private SirenService service;

	@BeforeEach
	void setUp() {
		world  = mock(World.class);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getName()).thenReturn("Bandit");
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		group = mock(CopGroup.class);
		when(group.getCops()).thenAnswer(inv -> cops);
		service = new SirenService(settings::get, (at, s) -> played.add(at), () -> now[0]);
	}

	private Location copAt(CopState state, double distance) {
		Location     at     = new Location(world, distance, 64, 0);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(at);
		when(entity.getName()).thenReturn("Unit" + cops.size());
		CopNpc cop = mock(CopNpc.class);
		when(cop.getCurrentState()).thenReturn(state);
		when(cop.getEntity()).thenReturn(entity);
		cops.add(cop);
		return at;
	}

	/** One tick per second for {@code seconds} seconds plus the tick at 0. */
	private void tickEverySecond(int seconds) {
		for (int i = 0; i <= seconds; i++) {
			service.tick(player, group);
			now[0] += SEC;
		}
	}

	@Test
	@DisplayName("a PURSUING cop 40 blocks away and 11 s of one-second ticks: the siren plays twice, at the cop")
	void farPursuerSirensEveryInterval() {
		Location at = copAt(CopState.PURSUING, 40);
		tickEverySecond(11);
		assertEquals(2, played.size());
		assertSame(at, played.get(0));
	}

	@Test
	@DisplayName("the first qualifying tick starts the cadence and is silent")
	void firstTickSilent() {
		copAt(CopState.PURSUING, 40);
		service.tick(player, group);
		assertTrue(played.isEmpty());
	}

	@Test
	@DisplayName("a PURSUING cop 20 blocks away (inside Min_Distance 24) never sirens")
	void closePursuerSilent() {
		copAt(CopState.PURSUING, 20);
		tickEverySecond(20);
		assertTrue(played.isEmpty());
	}

	@Test
	@DisplayName("only PURSUING cops count: a far POSTED cop is ignored, the 30 block PURSUING one sirens")
	void nonPursuingIgnored() {
		copAt(CopState.POSTED, 60);
		Location pursuer = copAt(CopState.PURSUING, 30);
		copAt(CopState.IDLE, 5);
		tickEverySecond(6);
		assertEquals(1, played.size());
		assertSame(pursuer, played.get(0));
	}

	@Test
	@DisplayName("the nearest pursuer decides: a close one silences a far one")
	void nearestPursuerDecides() {
		copAt(CopState.PURSUING, 80);
		copAt(CopState.PURSUING, 10);
		tickEverySecond(12);
		assertTrue(played.isEmpty());
	}

	@Test
	@DisplayName("a null group, an empty group and Siren.Enable false make no sound")
	void noGroupNoSound() {
		service.tick(player, null);
		now[0] += 10 * SEC;
		service.tick(player, null);
		assertTrue(played.isEmpty());

		tickEverySecond(12);
		assertTrue(played.isEmpty());

		copAt(CopState.PURSUING, 40);
		settings.set(new SirenSettings(false, 5, 24.0, "X", 3.0f, 1.0f));
		tickEverySecond(12);
		assertTrue(played.isEmpty());
	}

	@Test
	@DisplayName("a reload is seen: Interval_Seconds 2 sirens more often")
	void reloadIsSeen() {
		copAt(CopState.PURSUING, 40);
		settings.set(new SirenSettings(true, 2, 24.0, "X", 3.0f, 1.0f));
		tickEverySecond(6);
		assertEquals(3, played.size());
	}

	@Test
	@DisplayName("a pursuer in another world and a cop without an entity are skipped without throwing")
	void otherWorldOrEntitylessSkipped() {
		LivingEntity foreign = mock(LivingEntity.class);
		when(foreign.getLocation()).thenReturn(new Location(mock(World.class), 40, 64, 0));
		CopNpc far = mock(CopNpc.class);
		when(far.getCurrentState()).thenReturn(CopState.PURSUING);
		when(far.getEntity()).thenReturn(foreign);
		cops.add(far);
		CopNpc ghost = mock(CopNpc.class);
		when(ghost.getCurrentState()).thenReturn(CopState.PURSUING);
		cops.add(ghost);
		tickEverySecond(12);
		assertTrue(played.isEmpty());
	}

	@Test
	@DisplayName("when the pursuers go quiet the cadence resets: the next far pursuit waits a full interval again")
	void cadenceResetsWhenSilent() {
		copAt(CopState.PURSUING, 40);
		tickEverySecond(4);
		cops.clear();
		service.tick(player, group);
		now[0] += SEC;
		copAt(CopState.PURSUING, 40);
		service.tick(player, group);
		now[0] += 3 * SEC;
		service.tick(player, group);
		assertTrue(played.isEmpty());
	}
}
