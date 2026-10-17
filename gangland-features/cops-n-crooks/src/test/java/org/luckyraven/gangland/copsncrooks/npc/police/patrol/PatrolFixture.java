package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared mocks of the patrol tests: a world, a controllable clock, online players (some armed) and stateful beat-cop
 * mocks whose state, target and combat flag behave like the real fields. Install in {@code @BeforeEach}, close in
 * {@code @AfterEach}.
 */
public final class PatrolFixture implements AutoCloseable {

	public final BukkitStatics bukkit = BukkitStatics.install();
	public final World         world  = mock(World.class);
	public final long[]        clock  = {1_000_000L};
	/** Players that hold a weapon right now. */
	public final Set<UUID>     armed  = new HashSet<>();

	public PatrolFixture() {
		when(world.getName()).thenReturn("world");
		when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(true);
		when(world.getHighestBlockYAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(63);
	}

	public Location at(double x, double z) {
		return new Location(world, x, 64, z);
	}

	/** An online, valid, living player at (x, 64, z) in {@link #world}. */
	public Player player(double x, double z) {
		UUID   id     = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getName()).thenReturn("P" + id.toString().substring(0, 4));
		when(player.isOnline()).thenReturn(true);
		when(player.isValid()).thenReturn(true);
		when(player.isDead()).thenReturn(false);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenAnswer(inv -> at(x, z));
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
		return player;
	}

	/** Makes {@code Bukkit.getPlayer(id)} answer null (the player quit). */
	public void quit(Player player) {
		UUID id = player.getUniqueId();
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(null);
		when(player.isOnline()).thenReturn(false);
	}

	/**
	 * A valid beat cop at (x, 64, z) whose spawn location is {@code anchor}. It sees everything ({@code canSee} true),
	 * measures {@code distanceTo} from its position and has an idle navigator (not navigating).
	 */
	public CopNpc cop(CopState initial, double x, double z, Location anchor) {
		CopNpc                        cop          = mock(CopNpc.class);
		AtomicReference<CopState>     state        = new AtomicReference<>(initial);
		AtomicReference<UUID>         targetPlayer = new AtomicReference<>();
		AtomicReference<LivingEntity> targetEntity = new AtomicReference<>();
		AtomicBoolean                 forced       = new AtomicBoolean();
		AtomicBoolean                 valid        = new AtomicBoolean(true);

		when(cop.getCurrentState()).thenAnswer(inv -> state.get());
		doAnswer(inv -> {
			state.set(inv.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		when(cop.getGroup()).thenReturn(null);
		when(cop.getTargetPlayerId()).thenAnswer(inv -> targetPlayer.get());
		doAnswer(inv -> {
			targetPlayer.set(inv.getArgument(0));
			return null;
		}).when(cop).setTargetPlayerId(any());
		when(cop.getTargetEntity()).thenAnswer(inv -> targetEntity.get());
		doAnswer(inv -> {
			targetEntity.set(inv.getArgument(0));
			return null;
		}).when(cop).setTargetEntity(any());
		when(cop.isCombatForced()).thenAnswer(inv -> forced.get());
		doAnswer(inv -> {
			forced.set(inv.getArgument(0));
			return null;
		}).when(cop).setCombatForced(anyBoolean());

		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenAnswer(inv -> at(x, z));
		NPC       npc       = mock(NPC.class);
		Navigator navigator = mock(Navigator.class);
		when(navigator.isNavigating()).thenReturn(false);
		when(npc.getNavigator()).thenReturn(navigator);
		when(npc.getEntity()).thenReturn(body);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getNpc()).thenReturn(npc);
		when(cop.getEntity()).thenReturn(body);
		when(cop.getSpawnLocation()).thenReturn(anchor);
		when(cop.isValid()).thenAnswer(inv -> valid.get());
		when(cop.canSee(any(LivingEntity.class), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(true);
		when(cop.distanceTo(any(Player.class))).thenAnswer(inv -> {
			Player other = inv.getArgument(0);
			return at(x, z).distance(other.getLocation());
		});
		when(cop.distanceTo(any(LivingEntity.class))).thenAnswer(inv -> {
			LivingEntity other = inv.getArgument(0);
			return at(x, z).distance(other.getLocation());
		});
		return cop;
	}

	/** Marks a cop built by {@link #cop} as dead (its {@code isValid} turns false). */
	public static void kill(CopNpc cop) {
		when(cop.isValid()).thenReturn(false);
	}

	/** A {@link WantedMessages} whose {@code format} answers the key name, so a test can match the line sent. */
	public static WantedMessages messages() {
		WantedMessages messages = mock(WantedMessages.class);
		when(messages.format(any(WantedMessages.Key.class), anyMap()))
				.thenAnswer(inv -> ((WantedMessages.Key) inv.getArgument(0)).name());
		return messages;
	}

	@Override
	public void close() {
		bukkit.close();
	}
}
