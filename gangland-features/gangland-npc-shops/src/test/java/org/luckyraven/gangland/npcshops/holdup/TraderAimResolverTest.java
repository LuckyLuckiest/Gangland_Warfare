package org.luckyraven.gangland.npcshops.holdup;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npcshops.trader.TraderData;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The player looks down +Z (yaw 0, pitch 0) from the origin at eye height 1.62; traders stand at (x, 1.62, z).
 */
@DisplayName("TraderAimResolver - which trader is the player aiming at")
class TraderAimResolverTest {

	private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID ID_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private final HoldUpSettings settings = new HoldUpSettings(true, 6.0, 10.0, 10, 10, 0.2, 4, 200.0, 600.0, 300,
	                                                           "f", "d", "a", "c");

	private World             world;
	private Player            player;
	private List<TraderNpc>   npcs;
	private TraderAimResolver resolver;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		world  = mock(World.class);
		player = mock(Player.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getEyeLocation()).thenReturn(new Location(world, 0, 1.62, 0, 0f, 0f));
		npcs = new ArrayList<>();

		TraderManager manager = mock(TraderManager.class);
		doAnswer(inv -> {
			Consumer<TraderNpc> consumer = inv.getArgument(0);
			npcs.forEach(consumer);
			return null;
		}).when(manager).forEach(any());
		resolver = new TraderAimResolver(manager);
	}

	private LivingEntity add(UUID id, String name, double x, double z, World entityWorld, boolean spawned,
	                         boolean dead, boolean lineOfSight) {
		LivingEntity entity = mock(LivingEntity.class);
		Location     feet   = new Location(entityWorld, x, 0, z);
		when(entity.getWorld()).thenReturn(entityWorld);
		when(entity.getLocation()).thenReturn(feet);
		when(entity.getEyeLocation()).thenReturn(new Location(entityWorld, x, 1.62, z));
		when(entity.isDead()).thenReturn(dead);
		when(player.hasLineOfSight(entity)).thenReturn(lineOfSight);

		NPC npc = mock(NPC.class);
		when(npc.isSpawned()).thenReturn(spawned);
		when(npc.getEntity()).thenReturn(entity);
		npcs.add(new TraderNpc(new TraderData(id, "shop", feet, name, "trait"), npc));
		return entity;
	}

	@Test
	@DisplayName("one trader straight ahead inside Range is the target, carrying id, name and entity location")
	void aimedTraderIsTheTarget() {
		LivingEntity entity = add(ID_A, "Clerk", 0, 4, world, true, false, true);

		HoldUpTarget target = resolver.resolve(player, settings);

		assertNotNull(target);
		assertEquals(ID_A, target.traderId());
		assertEquals("Clerk", target.name());
		assertEquals(entity.getLocation(), target.location());
	}

	@Test
	@DisplayName("of two aimed traders the nearest wins, whatever the iteration order")
	void nearestWins() {
		add(ID_A, "Far", 0, 5, world, true, false, true);
		add(ID_B, "Near", 0, 2, world, true, false, true);

		HoldUpTarget target = resolver.resolve(player, settings);

		assertNotNull(target);
		assertEquals(ID_B, target.traderId());
	}

	@Test
	@DisplayName("nearest wins when the near trader is listed first")
	void nearestWinsNearFirst() {
		add(ID_B, "Near", 0, 2, world, true, false, true);
		add(ID_A, "Far", 0, 5, world, true, false, true);

		HoldUpTarget target = resolver.resolve(player, settings);

		assertNotNull(target);
		assertEquals(ID_B, target.traderId());
	}

	@Test
	@DisplayName("a trader beyond Range is not a target")
	void outOfRange() {
		add(ID_A, "Clerk", 0, 9, world, true, false, true);
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("a trader outside the Aim_Angle cone is not a target")
	void outsideAngle() {
		add(ID_A, "Clerk", 3, 4, world, true, false, true); // about 37 degrees off the aim
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("no line of sight means no target even when centred")
	void noLineOfSight() {
		add(ID_A, "Clerk", 0, 4, world, true, false, false);
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("a trader in another world is skipped")
	void otherWorld() {
		add(ID_A, "Clerk", 0, 4, mock(World.class), true, false, true);
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("an unspawned NPC and a dead entity are skipped, the live one behind them is still found")
	void unspawnedAndDeadAreSkipped() {
		add(ID_A, "Gone", 0, 2, world, false, false, true);
		add(ID_B, "Dead", 0, 3, world, true, true, true);
		assertNull(resolver.resolve(player, settings));

		add(UUID.randomUUID(), "Alive", 0, 5, world, true, false, true);
		HoldUpTarget target = resolver.resolve(player, settings);
		assertNotNull(target);
		assertEquals("Alive", target.name());
	}

	@Test
	@DisplayName("a player with no world gives null instead of an NPE")
	void nullWorld() {
		add(ID_A, "Clerk", 0, 4, world, true, false, true);
		when(player.getWorld()).thenReturn(null);
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("no traders at all gives null")
	void noTraders() {
		assertNull(resolver.resolve(player, settings));
	}

	@Test
	@DisplayName("a trader without a display name is reported as Trader")
	void unnamedTrader() {
		add(ID_A, null, 0, 4, world, true, false, true);
		HoldUpTarget target = resolver.resolve(player, settings);
		assertNotNull(target);
		assertEquals("Trader", target.name());
	}

}
