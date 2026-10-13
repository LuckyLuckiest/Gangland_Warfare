package org.luckyraven.gangland.civilians.listener.gang;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GR-40 listener half: Bartizan impacts follow the gang module's friendly-fire fact, not Gang.Enable. */
@DisplayName("GangAllyWeaponImpactListener")
class GangAllyWeaponImpactListenerTest {

	private final GangMembership membership = mock(GangMembership.class);
	private final GangAllyWeaponImpactListener listener = new GangAllyWeaponImpactListener(membership);
	private final Player shooter = mock(Player.class);
	private final Player target  = mock(Player.class);
	private final UUID   a       = UUID.randomUUID();
	private final UUID   b       = UUID.randomUUID();

	private WeaponRaytraceImpactEvent impact(org.bukkit.entity.Entity hit) {
		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(shooter);
		when(event.getHitEntity()).thenReturn(hit);
		when(shooter.getUniqueId()).thenReturn(a);
		when(target.getUniqueId()).thenReturn(b);
		when(target.getLocation()).thenReturn(mock(Location.class));
		return event;
	}

	@Test
	@DisplayName("factHonoured: pvpBlocked decides, whatever alliedOrSame says")
	void factHonoured() {
		when(membership.pvpBlocked(any(), any(), any())).thenReturn(true);
		WeaponRaytraceImpactEvent blocked = impact(target);

		listener.onGangMemberWeaponImpact(blocked);

		verify(blocked).setCancelled(true);

		when(membership.pvpBlocked(any(), any(), any())).thenReturn(false);
		when(membership.alliedOrSame(a, b)).thenReturn(true);
		WeaponRaytraceImpactEvent allowed = impact(target);

		listener.onGangMemberWeaponImpact(allowed);

		verify(allowed, never()).setCancelled(true);
	}

	@Test
	@DisplayName("a non-player hit entity is never cancelled")
	void npcUntouched() {
		when(membership.pvpBlocked(any(), any(), any())).thenReturn(true);
		WeaponRaytraceImpactEvent event = impact(mock(Zombie.class));

		listener.onGangMemberWeaponImpact(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("bartizanAbsent_classSkipped: the class is only built when Bartizan is present")
	void bartizanAbsent_classSkipped() {
		ListenerHandler handler = GangAllyWeaponImpactListener.class.getAnnotation(ListenerHandler.class);

		assertEquals("isBartizanAvailable", handler.condition());
	}
}
