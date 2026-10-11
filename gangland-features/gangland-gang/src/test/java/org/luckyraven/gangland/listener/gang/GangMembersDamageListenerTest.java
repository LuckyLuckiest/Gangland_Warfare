package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * GR-15: a member whose gang record is missing must not NPE the friendly-fire check. GR-39: a member's own arrow hits
 * him again. GR-41: fire set by a gangmate (Flame, Fire Aspect) follows the same rule.
 */
@DisplayName("GangMembersDamageListener")
class GangMembersDamageListenerTest {

	private final UserManager<Player> userManager = mock(UserManager.class);
	private final GangManager         gangManager = mock(GangManager.class);

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	private GangMembersDamageListener listener() {
		return new GangMembersDamageListener(userManager, gangManager);
	}

	@SuppressWarnings("unchecked")
	private Player member(int gangId) {
		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		return player;
	}

	@Test
	@DisplayName("damage_whenGangRecordMissing_noNpe: one side's gang id has no Gang, the hit is not cancelled")
	void damage_whenGangRecordMissing_noNpe() {
		Player damager = member(1);
		Player damaged = member(2);
		Gang   gang    = new Gang(1);
		gang.addAlly(new Gang(3));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

		when(event.getDamager()).thenReturn(damager);
		when(event.getEntity()).thenReturn(damaged);
		when(gangManager.getGang(1)).thenReturn(gang);
		when(gangManager.getGang(2)).thenReturn(null);

		assertDoesNotThrow(() -> listener().onGangMemberHitMembers(event));
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("selfShot_allowedByDefault: a member's own arrow is not cancelled (GR-39)")
	void selfShot_allowedByDefault() {
		Player me = member(1);
		Arrow  arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(me);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(me);

		listener().onGangMemberHitMembers(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("a gangmate's arrow is still cancelled while gang pvp is off")
	void gangmateArrow_cancelled() {
		Player shooter = member(1);
		Player victim  = member(1);
		Arrow  arrow   = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(shooter);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(victim);

		listener().onGangMemberHitMembers(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("combustBetweenGangmates_cancelled: Flame and Fire Aspect are stopped at the source (GR-41)")
	void combustBetweenGangmates_cancelled() {
		Player shooter = member(1);
		Player victim  = member(1);
		Arrow  arrow   = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(shooter);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityCombustByEntityEvent event = mock(EntityCombustByEntityEvent.class);
		when(event.getCombuster()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(victim);

		listener().onCombust(event);

		verify(event).setCancelled(true);
	}
}
