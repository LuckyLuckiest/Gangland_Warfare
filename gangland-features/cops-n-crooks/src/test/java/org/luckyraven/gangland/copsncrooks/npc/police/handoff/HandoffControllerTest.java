package org.luckyraven.gangland.copsncrooks.npc.police.handoff;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HandoffController}: the engaged to not-engaged edge that sets the spawn bias, the heading it reads and the
 * once-per-bias rule. Real {@link CopGroup}, mock cops (state is a settable field), a manual radio clock.
 */
@DisplayName("HandoffController")
class HandoffControllerTest {

	private final UUID              playerId = UUID.randomUUID();
	private final World             world    = mock(World.class);
	private final Player            player   = mock(Player.class);
	private final CopRadio          radio    = mock(CopRadio.class);
	private final CopManager        manager  = mock(CopManager.class);
	private final CopConfigProvider provider = mock(CopConfigProvider.class);
	private final CopGroup          group    = new CopGroup(playerId);
	private final long[]            clock    = {100_000L};

	private Location        at       = new Location(world, 0, 64, 0, 0f, 0f);
	private HandoffSettings settings = HandoffSettings.DEFAULT;

	private final Map<CopNpc, AtomicReference<CopState>> states = new HashMap<>();

	private final HandoffController controller;

	HandoffControllerTest() {
		group.setLevel(3);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Rob");
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenAnswer(i -> at);
		when(radio.now()).thenAnswer(i -> clock[0]);
		when(radio.compassWord(any(), any())).thenAnswer(i -> {
			Location from = i.getArgument(0);
			Location to   = i.getArgument(1);
			return Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ()) ? "east" : "south";
		});
		when(provider.getHandoffSettings()).thenAnswer(i -> settings);
		when(provider.getPursuitMaxDistance()).thenReturn(80.0);
		controller = new HandoffController(manager, radio, () -> provider);
	}

	private CopNpc cop(CopState state, double x) {
		CopNpc                    cop     = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(state);
		states.put(cop, current);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.getCurrentState()).thenAnswer(i -> current.get());
		group.getCops().add(cop);
		return cop;
	}

	private void set(CopNpc cop, CopState state) {
		states.get(cop).set(state);
	}

	/** The cop walks home from 200 blocks away: beyond Pursuit.Max_Distance. */
	private void leash(CopNpc cop) {
		set(cop, CopState.RETURNING);
		when(cop.getEntity().getLocation()).thenReturn(new Location(world, 200, 64, 0));
	}

	/** One AI tick: the player stands at (x, z) when the clock reads {@code seconds} past the start. */
	private void tick(double seconds, double x, double z) {
		clock[0] = 100_000L + (long) (seconds * 1000);
		at       = new Location(world, x, 64, z, at.getYaw(), at.getPitch());
		controller.tick(player, group);
	}

	/** A chaser engaged, then the leash breaks at {@code seconds}. */
	private CopNpc breakLeash(double seconds, double x, double z) {
		CopNpc chaser = cop(CopState.PURSUING, 5);
		tick(seconds - 0.5, x - 1, z);
		leash(chaser);
		tick(seconds, x, z);
		return chaser;
	}

	@Test
	@DisplayName("a leash break sets a bias ahead for ten seconds and radios the heading")
	void leashBreak_setsABiasAheadForTenSeconds_andRadiosHandoff() {
		CopNpc a = cop(CopState.PURSUING, 1);
		CopNpc b = cop(CopState.PURSUING, 2);
		tick(0, 0, 0);
		tick(1, 3, 0);
		leash(a);
		leash(b);
		tick(2, 6, 0);

		SpawnBias bias = group.biasAt(clock[0]);
		assertNotNull(bias);
		assertEquals(clock[0] + 10_000L, bias.until());
		assertEquals(60.0, bias.coneDegrees());
		assertTrue(bias.heading().getX() > 0 && Math.abs(bias.heading().getZ()) < 1e-9, "heading is east");
		verify(radio).sayFromLeader(group, "Handoff", Map.of("direction", "east"));
	}

	@Test
	@DisplayName("the bias carries the position at the hand-off, a copy that does not follow the player")
	void bias_carriesTheHandoffPosition_notTheLivePosition() {
		breakLeash(2, 6, 0);
		at.setX(500);
		at.setZ(500);

		Location seen = group.biasAt(clock[0]).lastSeen();
		assertEquals(6.0, seen.getX());
		assertEquals(0.0, seen.getZ());
	}

	@Test
	@DisplayName("a posted cop keeps the group engaged: no hand-off")
	void postedCopKeepsTheGroupEngaged() {
		cop(CopState.POSTED, 10);
		breakLeash(2, 6, 0);

		assertNull(group.biasAt(clock[0]));
		verify(radio, never()).sayFromLeader(any(), eq("Handoff"), any());
	}

	@Test
	@DisplayName("a cop still pursuing keeps the group engaged: no hand-off")
	void stillEngaged_noHandoff() {
		cop(CopState.PURSUING, 10);
		breakLeash(2, 6, 0);

		assertNull(group.biasAt(clock[0]));
	}

	@Test
	@DisplayName("a stood-down chase (level 0, or no cop still hunting him) is not a hand-off")
	void notWanted_noHandoff() {
		group.setLevel(0);
		breakLeash(2, 6, 0);
		assertNull(group.biasAt(clock[0]));

		group.setLevel(3);
		for (CopNpc cop : group.getCops()) {
			when(cop.getTargetPlayerId()).thenReturn(null);
			set(cop, CopState.PURSUING);
		}
		tick(3, 7, 0);
		for (CopNpc cop : group.getCops()) set(cop, CopState.RETURNING);
		tick(4, 8, 0);
		assertNull(group.biasAt(clock[0]));
	}

	@Test
	@DisplayName("the heading is read from the last two seconds only")
	void headingFromTheLastTwoSeconds() {
		CopNpc chaser = cop(CopState.PURSUING, 5);
		tick(0, 0, 0);
		tick(1, 0, 10);
		tick(2, 0, 20);   // walking south for two seconds ...
		tick(3, 10, 20);
		tick(4, 20, 20);  // ... then east for the last two
		leash(chaser);
		tick(4.5, 22, 20);

		Vector heading = group.biasAt(clock[0]).heading();
		assertTrue(heading.getX() > 0 && Math.abs(heading.getZ()) < 1e-9, "east, not south-east");
	}

	@Test
	@DisplayName("a suspect who barely moved is read by where he faces")
	void barelyMoved_usesFacing() {
		breakLeash(2, 0.5, 0); // yaw 0 faces +Z

		Vector heading = group.biasAt(clock[0]).heading();
		assertEquals(0.0, heading.getX(), 1e-6);
		assertEquals(1.0, heading.getZ(), 1e-6);
	}

	@Test
	@DisplayName("one hand-off per bias; a new one only after it expired")
	void oneHandoffPerBias() {
		CopNpc chaser = breakLeash(2, 6, 0);
		set(chaser, CopState.PURSUING);
		tick(3, 7, 0);
		leash(chaser);
		tick(4, 8, 0);
		verify(radio, times(1)).sayFromLeader(any(), eq("Handoff"), any());

		set(chaser, CopState.PURSUING);
		tick(13, 9, 0);
		leash(chaser);
		tick(14, 10, 0);
		verify(radio, times(2)).sayFromLeader(any(), eq("Handoff"), any());
	}

	@Test
	@DisplayName("Cops.Handoff.Enabled false: nothing is recorded or set")
	void disabled_doesNothing() {
		settings = new HandoffSettings(false, 2, 10, 60.0);
		breakLeash(2, 6, 0);

		assertNull(group.biasAt(clock[0]));
		verify(radio, never()).sayFromLeader(any(), eq("Handoff"), any());
	}
}
