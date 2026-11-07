package org.luckyraven.gangland.npcshops.banker;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Characterises the lifecycle methods of {@link BankerNpc} that wrap a Citizens {@link NPC}. */
@DisplayName("BankerNpc - lifecycle over a Citizens NPC")
class BankerNpcTest {

	private final World    world = mock(World.class);
	private final Location spawn = new Location(world, 0, 64, 0);
	private final NPC      npc   = mock(NPC.class);
	private final BankerNpc banker = new BankerNpc(new BankerData(UUID.randomUUID(), spawn, null), npc);

	@Test
	@DisplayName("faceLocation turns a spawned NPC and does nothing for an unspawned one")
	void faceLocation() {
		Location target = new Location(world, 5, 64, 5);

		when(npc.isSpawned()).thenReturn(false);
		banker.faceLocation(target);
		verify(npc, never()).faceLocation(any());

		when(npc.isSpawned()).thenReturn(true);
		banker.faceLocation(target);
		verify(npc).faceLocation(target);
	}

	@Test
	@DisplayName("resetPosition does not touch an unspawned NPC or one without an entity")
	void resetPosition_unspawnedOrNoEntity() {
		when(npc.isSpawned()).thenReturn(false);
		banker.resetPosition();
		verify(npc, never()).getEntity();

		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(null);
		banker.resetPosition();
	}

	@Test
	@DisplayName("resetPosition teleports back to the spawn only when more than 0.5 blocks away")
	void resetPosition_teleportsOnlyWhenFarEnough() {
		Entity entity = mock(Entity.class);
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(entity);

		when(entity.getLocation()).thenReturn(new Location(world, 0.5, 64, 0)); // exactly 0.25 squared
		banker.resetPosition();
		verify(entity, never()).teleport(any(Location.class));

		when(entity.getLocation()).thenReturn(new Location(world, 1, 64, 0));
		banker.resetPosition();
		verify(entity).teleport(spawn);
	}

	@Test
	@DisplayName("isAlive needs a spawned NPC with a live entity")
	void isAlive() {
		when(npc.isSpawned()).thenReturn(false);
		assertFalse(banker.isAlive());
		verify(npc, never()).getEntity();

		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(null);
		assertFalse(banker.isAlive());

		Entity entity = mock(Entity.class);
		when(npc.getEntity()).thenReturn(entity);
		when(entity.isDead()).thenReturn(true);
		assertFalse(banker.isAlive());

		when(entity.isDead()).thenReturn(false);
		assertTrue(banker.isAlive());
	}

	@Test
	@DisplayName("destroy despawns a spawned NPC before destroying it, and only destroys an unspawned one")
	void destroy() {
		when(npc.isSpawned()).thenReturn(true);
		banker.destroy();
		InOrder order = inOrder(npc);
		order.verify(npc).despawn();
		order.verify(npc).destroy();

		NPC other = mock(NPC.class);
		when(other.isSpawned()).thenReturn(false);
		new BankerNpc(new BankerData(UUID.randomUUID(), spawn, null), other).destroy();
		verify(other, never()).despawn();
		verify(other).destroy();
	}
}
