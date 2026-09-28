package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): a {@link CopGroup} owns one Keystone squad. A cop joins it when it is assigned to the group and
 * leaves it when it is released (despawned, killed, invalid); {@link CopNpc#squadFor} shares the squad only for the
 * group's own wanted player, so a retargeted cop or an entity target never feeds the group's last-known position.
 */
@DisplayName("CopGroup - squad membership")
class CopGroupSquadTest {

	@Test
	@DisplayName("an assigned cop joins the squad; a released cop is destroyed and leaves it")
	void addAndRelease_trackSquadMembership() {
		CopGroup group = new CopGroup(UUID.randomUUID());
		CopNpc   cop   = mock(CopNpc.class);

		group.add(cop);

		assertEquals(List.of(cop), group.getSquad().members());
		verify(cop).setGroup(group);

		group.release(cop, mock(NpcMarkManager.class));

		verify(cop).destroy(any());
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("squadFor shares the group squad only for the group's wanted player; leaveSquad drops the cop")
	void squadFor_sharesOnlyForTheGroupTarget_andLeaveSquadDropsTheCop() {
		UUID     wantedId = UUID.randomUUID();
		CopGroup group    = new CopGroup(wantedId);
		CopNpc   cop      = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);

		LivingEntity wanted = mock(LivingEntity.class);
		when(wanted.getUniqueId()).thenReturn(wantedId);

		assertSame(group.getSquad(), cop.squadFor(wanted));

		cop.leaveSquad();

		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("squadFor gives a non-group target the cop's own squad, seeded at its position; stable until the target changes")
	void squadFor_nonGroupTarget_getsOwnSquadSeededAtItsPosition() {
		UUID     wantedId = UUID.randomUUID();
		CopGroup group    = new CopGroup(wantedId);
		CopNpc   cop      = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);

		World        world    = mock(World.class);
		LivingEntity other    = mock(LivingEntity.class);
		Location     otherLoc = new Location(world, 5, 64, 5);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		when(other.getLocation()).thenReturn(otherLoc);

		NpcSquad solo = cop.squadFor(other);

		assertNotNull(solo);
		assertEquals(otherLoc, solo.lastKnownLocation());
		assertTrue(group.getSquad().isEmpty());
		assertSame(solo, cop.squadFor(other));

		LivingEntity another    = mock(LivingEntity.class);
		Location     anotherLoc = new Location(world, 9, 70, 9);
		when(another.getUniqueId()).thenReturn(UUID.randomUUID());
		when(another.getLocation()).thenReturn(anotherLoc);

		NpcSquad solo2 = cop.squadFor(another);

		assertNotSame(solo, solo2);
		assertEquals(anotherLoc, solo2.lastKnownLocation());
	}

	@Test
	@DisplayName("release leaves the squad before destroying, even when destroy throws")
	void release_leavesSquadBeforeDestroyThrows() {
		CopGroup       group       = new CopGroup(UUID.randomUUID());
		CopNpc         cop         = mock(CopNpc.class);
		NpcMarkManager markManager = mock(NpcMarkManager.class);
		group.add(cop);

		RuntimeException boom = new RuntimeException("boom");
		doThrow(boom).when(cop).destroy(any());

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> group.release(cop, markManager));

		assertSame(boom, thrown);
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("cops of a group turned on the same attacker share one attacker squad, seeded where he was, on the group's radio")
	void alertRetargetsGroupOntoNonWantedAttacker_allShareOneAttackerSquad() {
		CopGroup         group    = new CopGroup(UUID.randomUUID());
		NpcSquadListener listener = mock(NpcSquadListener.class);
		group.setListener(listener);
		CopNpc a = realCop(group);
		CopNpc b = realCop(group);

		LivingEntity attacker = entityAt(new Location(mock(World.class), 3, 64, 3));
		NpcSquad     shared   = a.squadFor(attacker);

		assertSame(shared, b.squadFor(attacker));
		assertNotSame(group.getSquad(), shared);
		assertEquals(attacker.getUniqueId(), group.attackerOf(shared));
		assertEquals(new Location(attacker.getLocation().getWorld(), 3, 64, 3), shared.lastKnownLocation());

		CopNpc downed = mock(CopNpc.class);
		shared.add(downed);
		shared.memberDown(downed);
		verify(listener).onSignal(eq(shared), any(NpcSquadSignal.class), eq(downed), any());
	}

	@Test
	@DisplayName("a cop that starts returning leaves the attacker squad too")
	void returningCop_leavesAttackerSquad() {
		CopGroup     group    = new CopGroup(UUID.randomUUID());
		CopNpc       cop      = realCop(group);
		LivingEntity attacker = entityAt(new Location(mock(World.class), 3, 64, 3));
		NpcSquad     shared   = cop.squadFor(attacker);
		shared.add(cop);

		cop.leaveSquad();

		assertTrue(shared.isEmpty());
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("a cop retargeted from an attacker back to the group's player leaves the attacker squad")
	void retargetToGroupPlayer_leavesAttackerSquad() {
		UUID         wantedId = UUID.randomUUID();
		CopGroup     group    = new CopGroup(wantedId);
		CopNpc       cop      = realCop(group);
		LivingEntity attacker = entityAt(new Location(mock(World.class), 3, 64, 3));
		NpcSquad     shared   = cop.squadFor(attacker);
		shared.add(cop);

		LivingEntity wanted = mock(LivingEntity.class);
		when(wanted.getUniqueId()).thenReturn(wantedId);

		assertSame(group.getSquad(), cop.squadFor(wanted));
		assertTrue(shared.isEmpty());
	}

	@Test
	@DisplayName("an attacker squad everyone left is pruned; one still manned is kept")
	void attackerSquadPrunedAfterAllLeave() {
		CopGroup     group  = new CopGroup(UUID.randomUUID());
		CopNpc       cop    = realCop(group);
		LivingEntity first  = entityAt(new Location(mock(World.class), 3, 64, 3));
		LivingEntity second = entityAt(new Location(mock(World.class), 9, 64, 9));
		cop.squadFor(first).add(cop);
		NpcSquad manned = group.attackerSquad(second.getUniqueId(), second.getLocation());
		manned.add(mock(CopNpc.class));
		cop.leaveSquad();

		group.pruneAttackerSquads();

		assertEquals(1, group.getAttackerSquads().size());
		assertSame(manned, group.getAttackerSquads().get(second.getUniqueId()));
	}

	@Test
	@DisplayName("detach moves a live cop out of the group and every squad without destroying it")
	void detach_leavesEverySquad_keepsTheCop() {
		CopGroup     group    = new CopGroup(UUID.randomUUID());
		CopNpc       cop      = mock(CopNpc.class);
		group.add(cop);
		LivingEntity attacker = entityAt(new Location(mock(World.class), 3, 64, 3));
		group.attackerSquad(attacker.getUniqueId(), attacker.getLocation()).add(cop);

		group.detach(cop);

		assertTrue(group.getCops().isEmpty());
		assertTrue(group.getSquad().isEmpty());
		assertTrue(group.getAttackerSquads().values().iterator().next().isEmpty());
		verify(cop).setGroup(null);
		verify(cop, never()).destroy(any());
	}

	@Test
	@DisplayName("backup: granted once per cooldown, lasts its duration, then queues its extra cops to go home")
	void backup_grantLastAndExpire() {
		CopGroup       group  = new CopGroup(UUID.randomUUID());
		BackupSettings backup = new BackupSettings(true, 1, 30_000, 60_000);

		assertTrue(group.requestBackup(1_000, backup));
		assertFalse(group.requestBackup(5_000, backup));
		assertEquals(1, group.backupExtra(5_000, backup));
		assertFalse(group.consumeBackupExpiry(5_000, backup));

		assertEquals(0, group.backupExtra(31_000, backup));
		assertTrue(group.consumeBackupExpiry(31_000, backup));
		assertEquals(1, group.getPendingRelease());
		assertFalse(group.consumeBackupExpiry(32_000, backup));

		assertTrue(group.requestBackup(61_000, backup));
		assertFalse(new CopGroup(UUID.randomUUID()).requestBackup(0, new BackupSettings(false, 1, 1, 1)));
	}

	private static CopNpc realCop(CopGroup group) {
		CopNpc cop = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);
		return cop;
	}

	private static LivingEntity entityAt(Location at) {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
		when(entity.getLocation()).thenReturn(at);
		return entity;
	}
}
