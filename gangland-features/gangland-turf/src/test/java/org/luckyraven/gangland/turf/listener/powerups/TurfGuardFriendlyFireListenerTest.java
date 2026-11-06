package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Friendly fire on turf NPCs, cancelled from the turf module so it works on a server with no cops module. Owners and
 * allies never hurt a guard or a Quartermaster, and a guard never hurts a guard of its own or an allied owner.
 */
@DisplayName("TurfGuardFriendlyFireListener — owner and allied fire on turf NPCs is cancelled (T-292)")
class TurfGuardFriendlyFireListenerTest {

	private static final int TURF_ID    = 7;
	private static final int OWNER_GANG = 1;
	private static final int ALLY_GANG  = 2;
	private static final int RIVAL_GANG = 3;

	private TurfPowerupManager         powerupNpcs;
	private TurfDefenderDeployer       defenders;
	private TurfManager                turfs;
	private UserLookupContract         users;
	private GangMembership             membership;
	private LivingEntity               defender;
	private TurfGuardFriendlyFireListener listener;

	@BeforeEach
	void setUp() {
		powerupNpcs = mock(TurfPowerupManager.class);
		defenders   = mock(TurfDefenderDeployer.class);
		turfs       = mock(TurfManager.class);
		users       = mock(UserLookupContract.class);
		membership  = mock(GangMembership.class);
		listener    = new TurfGuardFriendlyFireListener(powerupNpcs, defenders, turfs, users, membership);

		// Mock default int is 0, so every entity would look like turf 0 unless stubbed otherwise.
		when(defenders.findOwningTurfId(any())).thenReturn(-1);
		defender = mock(LivingEntity.class);
		when(defenders.findOwningTurfId(defender)).thenReturn(TURF_ID);

		Turf turf = mock(Turf.class);
		when(turf.isUnclaimed()).thenReturn(false);
		when(turf.getOwnerGangId()).thenReturn(OWNER_GANG);
		when(turfs.get(TURF_ID)).thenReturn(turf);
	}

	private Player playerInGang(int gangId) {
		Player player = mock(Player.class);
		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		when(users.findByPlayer(player)).thenReturn(user);
		return player;
	}

	@Test
	@DisplayName("owner member hitting a turf defender directly is cancelled")
	void ownerHit_cancelled_noCopsClassNeeded() {
		Player member = playerInGang(OWNER_GANG);
		EntityDamageByEntityEvent event = hit(member, defender);

		listener.onDamage(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("defender arrow (melee-style damage event) hitting an owning-gang member is cancelled")
	void defenderArrow_ownerMember_cancelled() {
		Player member = playerInGang(OWNER_GANG);
		Projectile arrow = mock(Projectile.class);
		when(arrow.getShooter()).thenReturn(defender);

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(member);

		listener.onNpcDamage(event);

		verify(event).setCancelled(true);
	}

	// ---- T-287: guards must not kill each other ----

	private Player guardOfTurf(int turfId, int ownerGang) {
		// Citizens guards are PLAYER-type entities with no gang User.
		Player guard = mock(Player.class);
		when(defenders.findOwningTurfId(guard)).thenReturn(turfId);
		Turf turf = mock(Turf.class);
		when(turf.isUnclaimed()).thenReturn(false);
		when(turf.getOwnerGangId()).thenReturn(ownerGang);
		when(turfs.get(turfId)).thenReturn(turf);
		return guard;
	}

	private EntityDamageByEntityEvent hit(Entity damager, Entity victim) {
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(damager);
		when(event.getEntity()).thenReturn(victim);
		return event;
	}

	@Test
	@DisplayName("guard hitting a guard of the same owner is cancelled")
	void guardOnGuard_sameOwner_cancelled() {
		Player victim = guardOfTurf(11, OWNER_GANG);
		EntityDamageByEntityEvent event = hit(defender, victim);

		listener.onNpcDamage(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("guard hitting a guard of an allied owner is cancelled")
	void guardOnGuard_allied_cancelled() {
		Player victim = guardOfTurf(12, ALLY_GANG);
		when(membership.gangsAllied(OWNER_GANG, ALLY_GANG)).thenReturn(true);
		EntityDamageByEntityEvent event = hit(defender, victim);

		listener.onNpcDamage(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("guard hitting a guard of a hostile owner is not cancelled")
	void guardOnGuard_hostile_notCancelled() {
		Player victim = guardOfTurf(13, RIVAL_GANG);
		EntityDamageByEntityEvent event = hit(defender, victim);

		listener.onNpcDamage(event);

		verify(event, never()).setCancelled(true);
	}
}
