package org.luckyraven.gangland.civilians.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.config.CivilianGroupConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises {@link CivilianGroup#getGroupCenter()}: the average of the valid members' locations, returned as a clone
 * of the first valid member's location (so world, yaw and pitch come from that member), or {@code null} when no member
 * is valid.
 */
@DisplayName("CivilianGroup - group center")
class CivilianGroupCenterTest {

	private CivilianGroup group;
	private World         world;

	@BeforeEach
	void setUp() {
		group = new CivilianGroup("g", new CivilianGroupConfig("g", "G", false, 0, 0, 10.0, Map.of()));
		world = mock(World.class);
	}

	private Location location(double x, double y, double z, float yaw, float pitch) {
		return new Location(world, x, y, z, yaw, pitch);
	}

	private CivilianNpc member(boolean valid, Location location) {
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.isValid()).thenReturn(valid);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(location);
		when(npc.getEntity()).thenReturn(entity);
		return npc;
	}

	@Test
	@DisplayName("an empty group has no center")
	void emptyGroup_hasNoCenter() {
		assertNull(group.getGroupCenter());
	}

	@Test
	@DisplayName("a group with no valid member has no center")
	void noValidMember_hasNoCenter() {
		group.addMember(member(false, location(1, 2, 3, 0, 0)));
		group.addMember(member(false, location(4, 5, 6, 0, 0)));

		assertNull(group.getGroupCenter());
	}

	@Test
	@DisplayName("the center averages the valid members and clones the first valid member's location")
	void center_averagesValidMembers_andClonesTheFirstValidLocation() {
		Location invalidFirst = location(1000, 1000, 1000, 90f, 45f);
		Location firstValid   = location(10, 20, 30, 15f, 5f);
		Location second       = location(20, 40, 60, 99f, 9f);
		Location invalidLast  = location(-500, -500, -500, 0f, 0f);
		group.addMember(member(false, invalidFirst));
		group.addMember(member(true, firstValid));
		group.addMember(member(true, second));
		group.addMember(member(false, invalidLast));

		Location center = group.getGroupCenter();

		assertEquals(15.0, center.getX());
		assertEquals(30.0, center.getY());
		assertEquals(45.0, center.getZ());
		assertEquals(15f, center.getYaw());
		assertEquals(5f, center.getPitch());
		assertSame(world, center.getWorld());
		assertNotSame(firstValid, center);
		// the member's own location is left alone
		assertEquals(10.0, firstValid.getX());
		assertEquals(20.0, firstValid.getY());
		assertEquals(30.0, firstValid.getZ());
	}

	@Test
	@DisplayName("a single valid member is its own center, as a copy")
	void singleValidMember_centerIsACopyOfItsLocation() {
		Location only = location(7, 8, 9, 1f, 2f);
		group.addMember(member(true, only));

		Location center = group.getGroupCenter();

		assertNotSame(only, center);
		assertEquals(only, center);
	}

}
