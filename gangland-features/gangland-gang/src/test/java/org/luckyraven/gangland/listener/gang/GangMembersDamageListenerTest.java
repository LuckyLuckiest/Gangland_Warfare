package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
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
 * GR-15: a member whose gang record is missing must not NPE the friendly-fire check.
 */
@DisplayName("GangMembersDamageListener - missing gang record")
class GangMembersDamageListenerTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	@Test
	@DisplayName("damage_whenGangRecordMissing_noNpe: one side's gang id has no Gang, the hit is not cancelled")
	@SuppressWarnings("unchecked")
	void damage_whenGangRecordMissing_noNpe() {
		Player              damager     = mock(Player.class);
		Player              damaged     = mock(Player.class);
		User<Player>        userDamager = mock(User.class);
		User<Player>        userDamaged = mock(User.class);
		UserManager<Player> userManager = mock(UserManager.class);
		GangManager         gangManager = mock(GangManager.class);
		Gang                gang        = new Gang(1);
		gang.addAlly(new Gang(3));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

		when(event.getDamager()).thenReturn(damager);
		when(event.getEntity()).thenReturn(damaged);
		when(userManager.getUser(damager)).thenReturn(userDamager);
		when(userManager.getUser(damaged)).thenReturn(userDamaged);
		when(userDamager.hasGang()).thenReturn(true);
		when(userDamaged.hasGang()).thenReturn(true);
		when(userDamager.getGangId()).thenReturn(1);
		when(userDamaged.getGangId()).thenReturn(2);
		when(gangManager.getGang(1)).thenReturn(gang);
		when(gangManager.getGang(2)).thenReturn(null);

		GangMembersDamageListener listener = new GangMembersDamageListener(userManager, gangManager);

		assertDoesNotThrow(() -> listener.onGangMemberHitMembers(event));
		verify(event, never()).setCancelled(true);
	}

}
