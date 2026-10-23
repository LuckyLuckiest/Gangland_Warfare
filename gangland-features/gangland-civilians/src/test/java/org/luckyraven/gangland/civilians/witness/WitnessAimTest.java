package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WitnessAim - armed, within range and looking at the caller (P-WIT-3)")
class WitnessAimTest {

	private final World world = mock(World.class);

	/** The caller stands {@code distance} blocks along +Z; the criminal at the origin looks along +Z (yaw 0). */
	private boolean aims(boolean armed, double distance, float lookYaw, double range, double angle) {
		Player criminal = mock(Player.class);
		Location eye = new Location(world, 0, 65.6, 0);
		eye.setYaw(lookYaw);
		eye.setPitch(0f);
		when(criminal.getEyeLocation()).thenReturn(eye);
		when(criminal.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(criminal.getWorld()).thenReturn(world);

		CivilianNpc  caller = mock(CivilianNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, 0, 64, distance));
		when(entity.getEyeLocation()).thenReturn(new Location(world, 0, 65.6, distance));
		when(entity.getWorld()).thenReturn(world);
		when(caller.getEntity()).thenReturn(entity);

		return new WitnessAim(player -> armed).aimsAt(criminal, caller, range, angle);
	}

	@Test
	@DisplayName("armed, 10 blocks away, looking straight at the caller: aiming")
	void straightAtCaller() {
		assertTrue(aims(true, 10, 0f, 16.0, 12.0));
	}

	@Test
	@DisplayName("10 degrees off still counts, 20 degrees off does not (Scare_Angle 12)")
	void angleLimit() {
		assertTrue(aims(true, 10, 10f, 16.0, 12.0));
		assertFalse(aims(true, 10, 20f, 16.0, 12.0));
	}

	@Test
	@DisplayName("looking the other way never counts")
	void lookingAway() {
		assertFalse(aims(true, 10, 180f, 16.0, 12.0));
	}

	@Test
	@DisplayName("beyond Scare_Range it does not count (16 blocks)")
	void rangeLimit() {
		assertTrue(aims(true, 15.5, 0f, 16.0, 12.0));
		assertFalse(aims(true, 17.0, 0f, 16.0, 12.0));
	}

	@Test
	@DisplayName("unarmed: never aiming, however well lined up")
	void unarmedNeverAims() {
		assertFalse(aims(false, 5, 0f, 16.0, 12.0));
	}

	@Test
	@DisplayName("a caller with no entity left is never aimed at")
	void noEntityNeverAims() {
		Player      criminal = mock(Player.class);
		CivilianNpc caller   = mock(CivilianNpc.class);
		when(caller.getEntity()).thenReturn(null);

		assertFalse(new WitnessAim(player -> true).aimsAt(criminal, caller, 16.0, 12.0));
	}

	@Test
	@DisplayName("different worlds: not aiming")
	void otherWorldNeverAims() {
		Player criminal = mock(Player.class);
		Location eye = new Location(world, 0, 65.6, 0);
		when(criminal.getEyeLocation()).thenReturn(eye);
		when(criminal.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(criminal.getWorld()).thenReturn(world);
		CivilianNpc  caller = mock(CivilianNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		World        other  = mock(World.class);
		when(entity.getLocation()).thenReturn(new Location(other, 0, 64, 5));
		when(entity.getEyeLocation()).thenReturn(new Location(other, 0, 65.6, 5));
		when(entity.getWorld()).thenReturn(other);
		when(caller.getEntity()).thenReturn(entity);

		assertFalse(new WitnessAim(player -> true).aimsAt(criminal, caller, 16.0, 12.0));
	}
}
