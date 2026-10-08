package org.luckyraven.gangland.copsncrooks.listener.turf;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
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
 * Red tests for the T-189 stray-round rule (0.16.1): a defender or Quartermaster firing at a cop must not hit a
 * protected player of the turf's owning or allied gang. Cops and other gangs are not protected and keep taking hits.
 */
@DisplayName("TurfFriendlyFireListener — turf NPC stray rounds spare protected players (T-189)")
class TurfFriendlyFireListenerTest {

	private static final int TURF_ID     = 7;
	private static final int OWNER_GANG  = 1;
	private static final int ALLY_GANG   = 2;
	private static final int RIVAL_GANG  = 3;

	private TurfPowerupManager   powerupNpcs;
	private TurfDefenderDeployer defenders;
	private TurfManager          turfs;
	private UserLookupContract   users;
	private GangMembership       membership;
	private LivingEntity         defender;
	private TurfFriendlyFireListener listener;

	@BeforeEach
	void setUp() {
		powerupNpcs = mock(TurfPowerupManager.class);
		defenders   = mock(TurfDefenderDeployer.class);
		turfs       = mock(TurfManager.class);
		users       = mock(UserLookupContract.class);
		membership  = mock(GangMembership.class);
		listener    = new TurfFriendlyFireListener(powerupNpcs, defenders, turfs, users, membership);

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
	@DisplayName("defender round hitting an owning-gang member is cancelled")
	void defenderRound_ownerMember_cancelled() {
		Player member = playerInGang(OWNER_GANG);

		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(defender);
		when(event.getHitEntity()).thenReturn(member);

		listener.onNpcWeaponImpact(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("defender round hitting an allied-gang member is cancelled")
	void defenderRound_allyMember_cancelled() {
		Player ally = playerInGang(ALLY_GANG);
		when(membership.gangsAllied(ALLY_GANG, OWNER_GANG)).thenReturn(true);

		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(defender);
		when(event.getHitEntity()).thenReturn(ally);

		listener.onNpcWeaponImpact(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("defender round hitting a cop (not a player) is not cancelled")
	void defenderRound_cop_notCancelled() {
		LivingEntity cop = mock(LivingEntity.class);

		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(defender);
		when(event.getHitEntity()).thenReturn(cop);

		listener.onNpcWeaponImpact(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("defender round hitting a rival-gang player is not cancelled")
	void defenderRound_rival_notCancelled() {
		Player rival = playerInGang(RIVAL_GANG);

		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(defender);
		when(event.getHitEntity()).thenReturn(rival);

		listener.onNpcWeaponImpact(event);

		verify(event, never()).setCancelled(true);
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
}
